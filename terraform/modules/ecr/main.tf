# One private image repository per service
resource "aws_ecr_repository" "this" {
  for_each = toset(var.repository_names)

  name = each.value

  # CI tags every image with the git commit SHA, so tags are unique in practice.
  # Kept MUTABLE so a re-run of a CI job can push the same tag again.
  #checkov:skip=CKV_AWS_51:CI pushes unique commit-SHA tags; mutable only to allow re-running a job
  image_tag_mutability = "MUTABLE"

  # Lets `terraform destroy` remove repositories that still contain images
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    #checkov:skip=CKV_AWS_136:AES256 (AWS-managed) encryption is sufficient for this project
    encryption_type = "AES256"
  }

  tags = {
    Name = each.value
  }
}

# Keep storage (and cost) small: only the newest images are kept
resource "aws_ecr_lifecycle_policy" "this" {
  for_each   = aws_ecr_repository.this
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only the newest ${var.images_to_keep} images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.images_to_keep
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
