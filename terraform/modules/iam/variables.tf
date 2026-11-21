variable "cluster_name" {
  description = "Name of the EKS cluster (scopes the CI eks:DescribeCluster permission)"
  type        = string
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the CI role, as owner/name"
  type        = string
}

variable "ecr_repository_arns" {
  description = "ECR repositories CI is allowed to push to"
  type        = list(string)
}
