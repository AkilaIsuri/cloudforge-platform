terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
}


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
}

module "public_subnet_b" {
  source                  = "./modules/subnet"
  vpc_id                  = module.vpc.vpc_id
  cidr_block              = var.public_subnet_b_cidr
  availability_zone       = var.public_subnet_b_az
  map_public_ip_on_launch = true
  subnet_name             = "public-subnet-b"
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
}

module "private_subnet_b" {
  source                  = "./modules/subnet"
  vpc_id                  = module.vpc.vpc_id
  cidr_block              = var.private_subnet_b_cidr
  availability_zone       = var.private_subnet_b_az
  map_public_ip_on_launch = false
  subnet_name             = "private-subnet-b"
}

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name = "cloudforge-nat-eip"
  }
}


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


module "iam" {
  source = "./modules/iam"
}



module "eks" {
  source = "./modules/eks"

  cluster_name     = "cloudforge-eks"
  cluster_role_arn = module.iam.eks_cluster_role_arn

  subnet_ids = [
    module.private_subnet_a.subnet_id,
    module.private_subnet_b.subnet_id
  ]

  node_role_arn = module.iam.eks_node_role_arn

  node_subnet_ids = [
    module.private_subnet_a.subnet_id,
    module.private_subnet_b.subnet_id
  ]
}


resource "aws_route_table_association" "private_a" {
  subnet_id      = module.private_subnet_a.subnet_id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = module.private_subnet_b.subnet_id
  route_table_id = aws_route_table.private.id
}

module "ecr" {
  source = "./modules/ecr"

  repository_name = var.ecr_repository_name
}