output "vpc_id" {
    description = "ID of the CloudForge VPC"
    value = aws_vpc.main.id
}

output "public_route_table_id" {
  description = "ID of the public route table"
  value       = aws_route_table.public.id
}