output "eks_cluster_role_arn" {
  description = "ARN of the EKS cluster IAM role"
  value       = aws_iam_role.eks_cluster.arn
}

output "eks_node_role_arn" {
  description = "ARN of the EKS node IAM role"
  value       = aws_iam_role.eks_node.arn
}

output "github_actions_role_arn" {
  description = "Role GitHub Actions assumes via OIDC"
  value       = aws_iam_role.github_actions.arn
}