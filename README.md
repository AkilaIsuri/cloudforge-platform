# CloudForge

## Project Overview

CloudForge is a production-ready Internal Developer Platform (IDP) built to demonstrate modern DevOps, DevSecOps, and Platform Engineering practices.

## Objectives

- Provision infrastructure using Terraform
- Deploy applications to Kubernetes
- Implement GitOps with Argo CD
- Build CI/CD with GitHub Actions
- Secure workloads using DevSecOps practices
- Monitor the platform with Prometheus, Grafana, Loki, and Tempo

## Architecture

Coming Soon

## Roadmap

- [ ] Local development with Docker Compose
- [ ] Local Kubernetes
- [ ] AWS Infrastructure
- [ ] Amazon EKS
- [ ] CI/CD
- [ ] GitOps
- [ ] Observability
- [ ] Security

## Running the Application on Local Kubernetes with Minikube

The application can be deployed locally on Kubernetes using Minikube.

# Prerequisites

Make sure the following are installed:

Docker Desktop

kubectl

Minikube

Verify the installations:

docker --version
kubectl version --client
minikube version

1. Start Minikube

Start the local Kubernetes cluster using the Docker driver:

minikube start --driver=docker

Verify the cluster:

kubectl get nodes

Expected:

NAME       STATUS   ROLES           AGE   VERSION
minikube   Ready    control-plane   ...   ...

2. Create Kubernetes Secret

Database credentials are stored using a Kubernetes Secret instead of hard-coding them in the Deployment files.

kubectl create secret generic postgres-secret \
  --from-literal=username=cloudforge \
  --from-literal=password=cloudforge

Verify:

kubectl get secrets

3. Build the Docker Image Inside Minikube

Configure the current terminal to use Minikube's Docker daemon:

eval $(minikube docker-env)

Navigate to the user-service:

cd applications/user-service

Build the image:

docker build -t cloudforge/user-service:1.0 .

This allows Kubernetes to use the locally built image without pushing it to Docker Hub.

4. Deploy the Kubernetes Resources

Navigate to the Kubernetes manifests:

cd ../../k8s

Apply the manifests:

kubectl apply -f .

This creates the PostgreSQL and user-service Deployments and Services.

5. Verify the Deployment

Check the Pods:

kubectl get pods

Check the Services:

kubectl get services

PostgreSQL runs internally using a ClusterIP Service, while the user-service is exposed using a NodePort.

6. Access the Application

Run:

minikube service user-service

Minikube will provide a URL for accessing the application locally.

Kubernetes Architecture

                    Minikube Cluster
                 ┌─────────────────────┐
                 │                     │
                 │  User Service       │
                 │  Spring Boot :8080  │
                 │         │           │
                 │         ▼           │
                 │  PostgreSQL :5432   │
                 │                     │
                 └─────────┬───────────┘
                           │
                       NodePort
                           │
                           ▼
                    Local Browser

Kubernetes Concepts Used

Deployment – manages application Pods.

Pod – runs the application container.

Service – provides stable networking between Pods.

ClusterIP – provides internal PostgreSQL communication.

NodePort – exposes the user-service outside the cluster.

Secret – stores database credentials separately from Deployment configuration.

Minikube – provides a local Kubernetes cluster for development and testing.

Troubleshooting

Minikube profile error: Minikube failed because its existing profile files were missing; the cluster was recreated using minikube delete followed by minikube start --driver=docker.

Secret not found: Pods entered CreateContainerConfigError because postgres-secret did not exist; the Secret was created using kubectl create secret.

ImagePullBackOff: Kubernetes could not pull the cloudforge/user-service:1.0 image because it was only available locally; the image was rebuilt inside Minikube's Docker environment using eval $(minikube docker-env).

Service unavailable: minikube service user-service initially failed because the user-service Pod was not running; kubectl get pods and kubectl describe pod were used to identify and troubleshoot the issue.

Useful Commands

# Start Minikube
minikube start --driver=docker

# Check cluster
kubectl get nodes

# Check Pods
kubectl get pods

# Check Services
kubectl get services

# View Pod details
kubectl describe pod <pod-name>

# View application logs
kubectl logs <pod-name>

# Apply Kubernetes manifests
kubectl apply -f .

# Access the application
minikube service user-service

# Stop Minikube
minikube stop