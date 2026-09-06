output "vpc_id" {
  description = "ID of the CloudForge VPC"
  value       = module.vpc.vpc_id
}

output "public_subnet_a_id" {
  description = "ID of the public subnet A"
  value       = module.public_subnet_a.subnet_id
}

output "public_subnet_b_id" {
  description = "ID of the public subnet B"
  value       = module.public_subnet_b.subnet_id
}

output "private_subnet_a_id" {
  description = "ID of the private subnet A"
  value       = module.private_subnet_a.subnet_id
}

output "private_subnet_b_id" {
  description = "ID of the private subnet B"
  value       = module.private_subnet_b.subnet_id
}

output "ecr_repository_url" {
  description = "ECR repository URL for the user service"
  value       = module.ecr.repository_url
}