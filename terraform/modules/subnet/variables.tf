variable "vpc_id" {
    description = "ID of the CloudForge VPC"
    type = string
}

variable "cidr_block" {
    description = "CIDR block for the subnet"
    type = string
}

variable "availability_zone" {
    description = "Availability zone for the subnet"
    type = string
}

variable "subnet_name" {
    description = "Name of the subnet"
    type = string
    
}

variable "map_public_ip_on_launch" {
  description = "Whether instances launched in the subnet should receive a public IP"
  type        = bool
  default     = false
}