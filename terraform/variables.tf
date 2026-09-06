variable "vpc_cidr" {
  description = "CIDR block for the CloudForge VPC"
  type        = string
}

variable "public_subnet_a_cidr" {
  description = "CIDR block for the public subnet A"
  type        = string
}

variable "public_subnet_a_az" {
  description = "Availability zone for the public subnet A"
  type        = string
}

variable "public_subnet_b_cidr" {
  description = "CIDR block for the public subnet B"
  type        = string
}

variable "public_subnet_b_az" {
  description = "Availability zone for the public subnet B"
  type        = string
}

variable "private_subnet_a_cidr" {
  description = "CIDR block for the private subnet A"
  type        = string
}

variable "private_subnet_a_az" {
  description = "Availability zone for the private subnet A"
  type        = string
}

variable "private_subnet_b_cidr" {
  description = "CIDR block for the private subnet B"
  type        = string
}

variable "private_subnet_b_az" {
  description = "Availability zone for the private subnet B"
  type        = string
}

variable "ecr_repository_name" {
  description = "Name of the ECR repository"
  type        = string
}