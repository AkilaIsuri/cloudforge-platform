variable "repository_names" {
  description = "Names of the ECR repositories (one per service image)"
  type        = list(string)
}

variable "images_to_keep" {
  description = "How many images to keep per repository"
  type        = number
  default     = 15
}
