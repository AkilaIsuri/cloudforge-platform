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