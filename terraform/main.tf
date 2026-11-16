terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  # Tag everything so CloudForge resources are easy to find (and cost) in the AWS console
  default_tags {
    tags = {
      Project   = "cloudforge"
      ManagedBy = "terraform"
    }
  }
}

locals {
  # Tags Kubernetes uses to find subnets for load balancers
  cluster_tag = { "kubernetes.io/cluster/${var.cluster_name}" = "shared" }
}

# ---------------------------------------------------------------------------
# Network: VPC, 2 public + 2 private subnets in 2 AZs, NAT gateway
# ---------------------------------------------------------------------------
module "vpc" {
  source   = "./modules/vpc"
  vpc_cidr = var.vpc_cidr
}

module "public_subnet_a" {
  source                  = "./modules/subnet"
  vpc_id                  = module.vpc.vpc_id
  cidr_block              = var.public_subnet_a_cidr
  availability_zone       = var.public_subnet_a_az
  map_public_ip_on_launch = true
  subnet_name             = "public-subnet-a"
  tags                    = merge(local.cluster_tag, { "kubernetes.io/role/elb" = "1" })
}

module "public_subnet_b" {
  source                  = "./modules/subnet"
  vpc_id                  = module.vpc.vpc_id
  cidr_block              = var.public_subnet_b_cidr
  availability_zone       = var.public_subnet_b_az
  map_public_ip_on_launch = true
  subnet_name             = "public-subnet-b"
  tags                    = merge(local.cluster_tag, { "kubernetes.io/role/elb" = "1" })
}

resource "aws_route_table_association" "public_a" {
  subnet_id      = module.public_subnet_a.subnet_id
  route_table_id = module.vpc.public_route_table_id
}

resource "aws_route_table_association" "public_b" {
  subnet_id      = module.public_subnet_b.subnet_id
  route_table_id = module.vpc.public_route_table_id
}

module "private_subnet_a" {
  source                  = "./modules/subnet"
  vpc_id                  = module.vpc.vpc_id
  cidr_block              = var.private_subnet_a_cidr
  availability_zone       = var.private_subnet_a_az
  map_public_ip_on_launch = false
  subnet_name             = "private-subnet-a"
  tags                    = merge(local.cluster_tag, { "kubernetes.io/role/internal-elb" = "1" })
}

module "private_subnet_b" {
  source                  = "./modules/subnet"
  vpc_id                  = module.vpc.vpc_id
  cidr_block              = var.private_subnet_b_cidr
  availability_zone       = var.private_subnet_b_az
  map_public_ip_on_launch = false
  subnet_name             = "private-subnet-b"
  tags                    = merge(local.cluster_tag, { "kubernetes.io/role/internal-elb" = "1" })
}

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name = "cloudforge-nat-eip"
  }
}

# Lets nodes in the private subnets reach the internet (pull images, AWS APIs)
resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = module.public_subnet_a.subnet_id

  tags = {
    Name = "cloudforge-nat-gateway"
  }

  depends_on = [module.vpc]
}

resource "aws_route_table" "private" {
  vpc_id = module.vpc.vpc_id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }

  tags = {
    Name = "cloudforge-private-rt"
  }
}

resource "aws_route_table_association" "private_a" {
  subnet_id      = module.private_subnet_a.subnet_id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = module.private_subnet_b.subnet_id
  route_table_id = aws_route_table.private.id
}

# ---------------------------------------------------------------------------
# Container registry: one ECR repository per service image
# ---------------------------------------------------------------------------
module "ecr" {
  source           = "./modules/ecr"
  repository_names = var.ecr_repository_names
}

# ---------------------------------------------------------------------------
# IAM: EKS roles + GitHub Actions OIDC role (push to the ECR repositories above)
# ---------------------------------------------------------------------------
module "iam" {
  source              = "./modules/iam"
  cluster_name        = var.cluster_name
  github_repository   = var.github_repository
  ecr_repository_arns = module.ecr.repository_arns
}

# ---------------------------------------------------------------------------
# EKS: control plane + managed node group in the private subnets + EBS CSI driver
# ---------------------------------------------------------------------------
module "eks" {
  source = "./modules/eks"

  cluster_name     = var.cluster_name
  cluster_role_arn = module.iam.eks_cluster_role_arn
  subnet_ids = [
    module.private_subnet_a.subnet_id,
    module.private_subnet_b.subnet_id,
  ]

  node_role_arn = module.iam.eks_node_role_arn
  node_subnet_ids = [
    module.private_subnet_a.subnet_id,
    module.private_subnet_b.subnet_id,
  ]
  node_instance_types = var.node_instance_types
  node_desired_size   = var.node_desired_size
  node_min_size       = var.node_min_size
  node_max_size       = var.node_max_size

  # Roles and their policies must exist before the cluster and nodes are created,
  # and nodes need the NAT route to join the cluster
  depends_on = [
    module.iam,
    aws_route_table_association.private_a,
    aws_route_table_association.private_b,
  ]
}
