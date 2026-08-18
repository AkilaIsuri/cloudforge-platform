resource "aws_eks_cluster" "main" {
  name     = var.cluster_name
  role_arn = var.cluster_role_arn

  vpc_config {
    subnet_ids = var.subnet_ids
  }

  tags = {
    Name = var.cluster_name
  }
}

resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.cluster_name}-nodes"
  node_role_arn   = var.node_role_arn

  subnet_ids = var.node_subnet_ids

  instance_types = ["t3.small"]

  scaling_config {
    desired_size = 2
    min_size     = 1
    max_size     = 2
  }

  tags = {
    Name = "${var.cluster_name}-nodes"
  }
}