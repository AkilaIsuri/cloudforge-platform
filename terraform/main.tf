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
  source            = "./modules/subnet"
  vpc_id            = module.vpc.vpc_id
  cidr_block        = var.public_subnet_a_cidr
  availability_zone = var.public_subnet_a_az
  subnet_name       = "public-subnet-a"
}

module "public_subnet_b" {
  source            = "./modules/subnet"
  vpc_id            = module.vpc.vpc_id
  cidr_block        = var.public_subnet_b_cidr
  availability_zone = var.public_subnet_b_az
  subnet_name       = "public-subnet-b"
}

resource "aws_route_table_association" "public_a" {
  subnet_id      = module.public_subnet_a.subnet_id
  route_table_id = module.vpc.public_route_table_id
}

resource "aws_route_table_association" "public_b" {
  subnet_id      = module.public_subnet_b.subnet_id
  route_table_id = module.vpc.public_route_table_id
}
