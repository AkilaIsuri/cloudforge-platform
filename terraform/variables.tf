variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
  default     = "cloudforge-eks"
}

variable "github_repository" {
  description = "GitHub repository (owner/name) whose main branch may push images via OIDC"
  type        = string
  default     = "AkilaIsuri/cloudforge-platform"
}

# --- Network ---------------------------------------------------------------

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

# --- Container registry ------------------------------------------------------

variable "ecr_repository_names" {
  description = "One ECR repository per service image"
  type        = list(string)
  default = [
    "user-service",
    "product-service",
    "order-service",
    "payment-service",
    "api-gateway",
    "frontend",
  ]
}

# --- EKS worker nodes ----------------------------------------------------------

variable "node_instance_types" {
  description = "EC2 instance types for the worker nodes"
  type        = list(string)
  default     = ["t3.large"]
}

variable "node_desired_size" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 2
}

variable "node_min_size" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 1
}

variable "node_max_size" {
  description = "Maximum number of worker nodes"
  type        = number
  default     = 3
}
