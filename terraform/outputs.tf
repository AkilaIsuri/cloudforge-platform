output "vpc_id" {
  description = "ID of the CloudForge VPC"
  value       = module.vpc.vpc_id
}

output "private_subnet_ids" {
  description = "Private subnets (EKS nodes)"
  value       = [module.private_subnet_a.subnet_id, module.private_subnet_b.subnet_id]
}

output "public_subnet_ids" {
  description = "Public subnets (internet-facing load balancers)"
  value       = [module.public_subnet_a.subnet_id, module.public_subnet_b.subnet_id]
}

output "cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "ecr_registry" {
  description = "ECR registry host (imageRepositoryPrefix in values-eks.yaml)"
  value       = module.ecr.registry
}

output "ecr_repository_urls" {
  description = "ECR repository URL per service"
  value       = module.ecr.repository_urls
}

output "github_actions_role_arn" {
  description = "IAM role used by GitHub Actions (role-to-assume in the workflow)"
  value       = module.iam.github_actions_role_arn
}

output "configure_kubectl" {
  description = "Run this to point kubectl at the new cluster"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}
