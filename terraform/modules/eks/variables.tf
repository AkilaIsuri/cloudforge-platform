variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
}

variable "cluster_role_arn" {
  description = "IAM role ARN for the EKS cluster"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for the EKS cluster"
  type        = list(string)
}

variable "node_role_arn" {
  description = "IAM role ARN for EKS worker nodes"
  type        = string
}

variable "node_subnet_ids" {
  description = "Private subnet IDs for EKS worker nodes"
  type        = list(string)
}