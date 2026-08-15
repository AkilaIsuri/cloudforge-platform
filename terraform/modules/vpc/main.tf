resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr

  tags = {
    Name = "cloudforge-vpc"
  }
} 

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "cloudforge-igw"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "cloudforge-public-rt"
  }
}