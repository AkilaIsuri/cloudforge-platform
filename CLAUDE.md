# CloudForge – Internal Developer Platform

A DevSecOps final project: a multi-service e-commerce app built, scanned, deployed
and monitored end to end on AWS.

## Project layout

- `applications/` – every container listens on port 8080.
  - `user-service`, `product-service`, `order-service`, `payment-service` – Spring Boot 3.5,
    Java 21, Maven. One Postgres database each (`users_db`, `products_db`, `orders_db`, `payments_db`).
  - `api-gateway` – Spring Cloud Gateway (WebFlux). Routes `/api/<name>/**` to each service.
  - `frontend` – the "VoltCart" shop: React (Vite) served by non-root nginx, which proxies `/api`
    to the gateway. Product images are SVGs in `frontend/public/images/products/`.
  - Order flow: order-service checks the user, reserves stock (product-service), charges
    payment-service (mock: declines amounts over 5000), and releases stock if payment fails.
- `database/init/` – SQL that creates the four databases on first Postgres start.
- `docker-compose.yml` – full local stack (`docker compose up --build`, shop on :3000).
- `terraform/` – AWS infrastructure in us-east-1: VPC, public/private subnets, NAT,
  EKS cluster `cloudforge-eks`, ECR, IAM (incl. GitHub OIDC role).
- `helm/cloudforge/` – Helm chart for the app. CI writes the image tag into `values.yaml`.
- `helm/monitoring/` – kube-prometheus-stack values (release name `monitoring`).
- `.github/workflows/` – GitHub Actions: build → push to ECR → bump Helm tag → commit.
- Argo CD (in the cluster) watches this repo and deploys the Helm chart.
- Kubernetes namespace: `cloudforge`.

## Rules

- Before acting, explain what you are about to do and why, in 1-2 plain sentences.
- Never run `terraform apply` or `terraform destroy` without showing the plan and asking first.
- Never `kubectl apply` app changes to EKS directly. Changes go through Git and Argo CD.
- CI authenticates to AWS with OIDC only. Never add AWS access keys to workflows or secrets.
- Never put passwords, tokens or other secrets in Git.
- Every service must expose Actuator health (liveness/readiness) and `/actuator/prometheus`.
- Keep the services consistent: when changing one service's structure, say whether the
  others need the same change.
- When a problem is fixed, add it to `docs/Issues.md` as **Problem / Root cause / Solution**.
