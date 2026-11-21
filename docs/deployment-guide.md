# CloudForge – Deployment Guide

From a laptop to Amazon EKS in four stages. Each stage builds on the previous one.

| Stage | Runs on | Entry point |
|---|---|---|
| [1. Docker Compose](#1-docker-compose) | Your machine | http://localhost:3000 |
| [2. Local Kubernetes](#2-local-kubernetes) | Docker Desktop Kubernetes | http://localhost:30080 |
| [3. AWS with Terraform](#3-aws-with-terraform) | Amazon EKS | AWS load balancer URL |
| [4. GitOps](#4-gitops-continuous-delivery) | GitHub Actions + Argo CD | every merge to `main` |
| [5. Teardown](#5-teardown) | | |

## Prerequisites

| Tool | Used for |
|---|---|
| Docker Desktop (with Kubernetes enabled for stage 2; give it ≥ 6 GB RAM, 4 CPUs) | stages 1–2 |
| `kubectl`, `helm` 3+ | stages 2–4 |
| `terraform` ≥ 1.10, `aws` CLI v2 with credentials (`aws sts get-caller-identity`) | stage 3 |
| A GitHub repository fork, a SonarCloud project, the `SONAR_TOKEN` secret | stage 4 |

## 1. Docker Compose

```bash
cp .env.example .env              # optional: set your own Postgres password
docker compose up --build
```

- **Shop:** http://localhost:3000 · **Gateway:** http://localhost:8080/api/products
- Services are also published on 8081–8084, Postgres on 5433.
- Postgres runs `database/init/01-create-databases.sql` on its first start (4 databases); each
  service creates its tables (`ddl-auto=update`); product-service seeds 8 products.
- Stop: `docker compose stop` (keeps data) · reset everything: `docker compose down -v`.

## 2. Local Kubernetes

Uses the images built in stage 1 (`cloudforge/<service>:local`).

```bash
kubectl config use-context docker-desktop

# Monitoring first, so the chart's ServiceMonitor, alerts and dashboard are created
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace -f helm/monitoring/values.yaml

# Namespace + database credentials (random password, never stored in Git)
kubectl create namespace cloudforge
kubectl create secret generic postgres-secret -n cloudforge --from-literal=username=cloudforge --from-literal=password="$(openssl rand -base64 18 | tr -d '/+=')"

# VoltCart
helm upgrade --install cloudforge helm/cloudforge -n cloudforge -f helm/cloudforge/values-local.yaml
kubectl get pods -n cloudforge -w          # wait for 7 pods 1/1 Running
```

- **Shop:** http://localhost:30080 (or `kubectl port-forward svc/frontend -n cloudforge 8088:80`).
- After rebuilding images with the same `local` tag, restart the pods:
  `kubectl rollout restart deployment -n cloudforge`.
- Grafana: `kubectl port-forward -n monitoring svc/monitoring-grafana 3001:80`, user `admin`,
  password `kubectl get secret -n monitoring monitoring-grafana -o jsonpath='{.data.admin-password}' | base64 -d`.

## 3. AWS with Terraform

### 3.1 Create the infrastructure

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars     # network ranges (git-ignored)
terraform init
terraform plan -out=tfplan                       # review: ~42 resources to add
terraform apply tfplan                           # ~20 minutes
```

Creates the VPC, subnets, NAT gateway, EKS cluster with 2 × m7i-flex.large nodes, EBS CSI driver,
6 ECR repositories and the IAM roles, including the GitHub Actions OIDC role.

> **AWS Free Tier plan accounts** can only launch free-tier-eligible instance types – that is why
> the default is `m7i-flex.large` (see [Issues #7](Issues.md#7-eks-node-group-create_failed-instance-type-not-eligible-for-free-tier)).
> Cost while running: roughly $0.35 per hour.

### 3.2 Connect and bootstrap the cluster

```bash
$(terraform output -raw configure_kubectl)       # adds the EKS context and switches to it
kubectl config current-context                   # …:cluster/cloudforge-eks  – check before every install!
kubectl get nodes                                # 2 × Ready
cd ..

helm repo add argo https://argoproj.github.io/argo-helm
helm repo update
helm upgrade --install argocd argo/argo-cd -n argocd --create-namespace
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace -f helm/monitoring/values.yaml

kubectl create namespace cloudforge
kubectl create secret generic postgres-secret -n cloudforge --from-literal=username=cloudforge --from-literal=password="$(openssl rand -base64 18 | tr -d '/+=')"
kubectl describe secret postgres-secret -n cloudforge     # must list BOTH password and username
```

> Paste the secret command as **one line**. A broken multi-line paste once created the secret
> without its `password` key.

### 3.3 Configure the repository

- `helm/cloudforge/values-eks.yaml` → `global.imageRepositoryPrefix`: the `ecr_registry` output.
- GitHub → Settings → Secrets and variables → Actions:
  - secret `SONAR_TOKEN`; optional variables `AWS_ACCOUNT_ID`, `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY`.
- `terraform/variables.tf` → `github_repository` must match your `owner/repo` (OIDC trust).

### 3.4 First release and Argo CD

1. Merge a pull request into `main`. The **CI/CD** workflow builds, scans and pushes the 6 images and
   commits the new tag into `values-eks.yaml`.
2. Register the application with Argo CD (once):
   ```bash
   kubectl apply -f argocd/cloudforge-app.yaml
   kubectl get applications -n argocd             # cloudforge  Synced  Healthy
   ```
3. Get the public address and allow it in the gateway's CORS list:
   ```bash
   kubectl get svc frontend -n cloudforge         # EXTERNAL-IP = load balancer DNS name
   ```
   Put `http://<that name>` into `services.api-gateway.env.CORS_ALLOWED_ORIGINS` in
   `values-eks.yaml` and merge it – otherwise registering and ordering return **403**
   ([Issues #2](Issues.md#2-403-forbidden-when-registering-a-user-on-kubernetes)).
4. The backup PVC stays `Pending` until the first backup job runs (gp3 uses `WaitForFirstConsumer`).
   Run one immediately:
   `kubectl create job -n cloudforge --from=cronjob/postgres-backup backup-1`.

### 3.5 UIs (all through port-forward – nothing except VoltCart is public)

| UI | Command | URL / login |
|---|---|---|
| Argo CD | `kubectl port-forward svc/argocd-server -n argocd 8443:443` | https://localhost:8443, `admin` / `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' \| base64 -d` |
| Grafana | `kubectl port-forward -n monitoring svc/monitoring-grafana 3001:80` | http://localhost:3001, `admin` / see stage 2 |
| Prometheus | `kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090` | http://localhost:9090 |
| Alertmanager | `kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-alertmanager 9093:9093` | http://localhost:9093 |

> A port-forward stays connected to the cluster that was active when it started. Stop old tunnels
> when switching between the local cluster and EKS ([Issues #11](Issues.md#11-alert-not-working-on-eks--the-browser-showed-the-local-clusters-prometheus)).

## 4. GitOps (continuous delivery)

After the setup, **every change reaches EKS through Git only**:

```
feature branch → pull request (tests, SonarCloud, IaC scans) → merge
  → CI: 6 images → Trivy → ECR → commit imageTag → Argo CD → rolling update on EKS
```

- **Rollback:** `git revert` the change (or the bot's `deploy:` commit) and merge; Argo CD applies it.
  Argo CD's *History and rollback* shows earlier revisions.
- **Manual changes are reverted:** `kubectl scale`/`edit` on Argo-managed resources is undone within
  seconds (self-heal). Change `values-eks.yaml` instead.
- **Trivy blocks a release** when an image has a fixable CRITICAL vulnerability. Patch the dependency
  or base image; accept only with a reason and expiry in `.trivyignore` (see [security.md](security.md)).

## 5. Teardown

Delete in this order – Kubernetes created the AWS load balancer and EBS volumes, so they must be
removed before Terraform deletes the VPC:

```bash
kubectl delete -f argocd/cloudforge-app.yaml      # finalizer deletes all app resources incl. the load balancer
kubectl delete pvc --all -n cloudforge             # EBS volumes (backups PVC is kept by Helm policy)
kubectl get svc -A | grep LoadBalancer             # must be empty
cd terraform && terraform destroy                  # ~15 minutes
```

Keep `terraform.tfstate`; never commit it, `terraform.tfvars` or `tfplan`.
