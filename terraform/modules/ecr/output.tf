output "repository_urls" {
  description = "Repository URL per service"
  value       = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}

output "repository_arns" {
  description = "Repository ARNs (used to scope the CI push permissions)"
  value       = [for repo in aws_ecr_repository.this : repo.arn]
}

output "registry" {
  description = "Registry host, e.g. 123456789012.dkr.ecr.us-east-1.amazonaws.com"
  value       = split("/", values(aws_ecr_repository.this)[0].repository_url)[0]
}
