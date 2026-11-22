# CloudForge – Security (DevSecOps)

Security is built into each stage – code, dependencies, images, infrastructure, pipeline and runtime –
rather than checked at the end.

## 1. Controls by stage

| Stage | Control | Where |
|---|---|---|
| **Code** | SonarCloud static analysis: bugs, vulnerabilities, security hotspots, coverage (JaCoCo) on every PR | `ci-cd.yml` → `sonarcloud` |
| **Dependencies** | Maven-managed versions with explicit pins for patched libraries; npm **lock file** + `npm ci --ignore-scripts`; **Dependabot** weekly update PRs (actions, Docker, Maven, npm) | `pom.xml`, `package-lock.json`, `.github/dependabot.yml` |
| **Images** | **Trivy security gate**: a release is blocked if any image has a *fixable* CRITICAL vulnerability; multi-stage builds; non-root runtime users; ECR scan on push | `ci-cd.yml` → `build-scan-push`, Dockerfiles |
| **Infrastructure as code** | **Checkov** on Terraform and on the rendered Helm chart; **Trivy config** scan of Terraform, Helm, Dockerfiles (report-only) | `iac-security.yml` |
| **Pipeline / supply chain** | AWS access via **OIDC** (short-lived, no stored keys), role limited to the 6 ECR repositories and the `main` branch; all actions pinned to **commit SHAs**; Checkov image pinned by **digest**; least-privilege `permissions:` per job | `ci-cd.yml`, `terraform/modules/iam` |
| **Deployment** | Only Argo CD deploys (GitOps); manual changes are reverted (self-heal); every change is reviewed in a PR | `argocd/cloudforge-app.yaml` |
| **Runtime – pods** | Non-root, `allowPrivilegeEscalation: false`, all capabilities dropped, `seccompProfile: RuntimeDefault`, read-only root filesystem (Spring), no service-account token mounted, CPU/memory/ephemeral-storage limits | `helm/cloudforge/templates` |
| **Runtime – network** | Nodes in private subnets (no public IPs); only the frontend is exposed; all other services are ClusterIP; Argo CD / Grafana / Prometheus only via `kubectl port-forward` | Terraform, Helm |
| **Data** | Credentials in a Kubernetes Secret created outside Git (random password); EBS volumes **encrypted** (gp3 StorageClass); ECR encrypted (AES-256) | Helm, Terraform |
| **Repository hygiene** | `.gitignore` for state, plans, tfvars, `.env`; `.trivyignore` entries need a reason and an expiry | `.gitignore`, `.trivyignore` |

## 2. Results

### Trivy (image security gate)

The gate worked as intended: after the first merge it **blocked** the release – no vulnerable image
reached ECR. Findings were then remediated:

| Image(s) | Found | Resolution |
|---|---|---|
| user, product, order, payment | 9 CRITICAL (Tomcat 10.1.43 ×7, Spring Framework 6.2.9 ×2) | Spring Boot 3.5.4 → 3.5.16, Tomcat pinned to 10.1.62; 2 accepted (below) – [Issues #8](Issues.md#8-trivy-blocked-the-release-9-critical-cves-in-the-spring-boot-images) |
| api-gateway | 3 CRITICAL (Netty, Bouncy Castle, Spring WebFlux) | Netty 4.1.141, Bouncy Castle 1.87; 1 accepted – [Issues #9](Issues.md#9-trivy-blocked-the-api-gateway-image-netty-bouncy-castle-webflux-cves) |
| frontend | 5 CRITICAL (nginx ×4, brotli) | brotli upgraded in the image; nginx ×4 accepted until the patched base image exists – [Issues #10](Issues.md#10-trivy-blocked-the-frontend-image-nginx-and-brotli-in-the-base-image) |

### SonarCloud

First analysis (PR #1): **22 reliability/security findings, 0 security hotspots, coverage 42.7 %**
(quality gate failed on coverage, reliability C, security C). Remediated in one change:

| Finding | Count | Fix |
|---|---|---|
| GitHub Actions not pinned to a full commit SHA | 7 | All actions pinned to SHAs + Dependabot |
| Dependencies installed without a lock file / with install scripts (npm, pip) | 6 | `package-lock.json` + `npm ci --ignore-scripts`; Checkov from a pinned image instead of `pip install` |
| Containers without ephemeral-storage request/limit | 5 | Requests and limits for all 9 container types |
| Unhandled promises, buttons without `type` (React) | 4 | `void run(...)`, explicit `type` |
| Client-side request forgery (user ID from browser storage into a URL) | 1 | IDs validated as positive integers, query built with `URLSearchParams` |

After this change SonarCloud closed **23 issues** on `main`: **0 bugs (reliability A)**, maintainability
**A**, **0 security hotspots**, **0 % duplication**, **0 new issues**. The 29 issues still open were all
in **legacy files no longer used** – the early Kompose/Minikube manifests (`k8s/`, root
`*-deployment.yaml` with a plain-text demo password) and a copy of the kube-prometheus-stack default
values (example password hashes from the upstream chart). These files were deleted. The demo password
`cloudforge` remains in Git history; it was only ever used for local development – the clusters use a
random password from a Kubernetes Secret.

The quality gate still fails on one condition: **coverage of new code** (the frontend has no tests –
see [known gaps](#4-known-gaps-and-future-work)). Overall coverage: **40 %**.

### Dependabot

The first Dependabot run opened **31 pull requests at once**, including major upgrades that would
have broken the build (Spring Boot 3.5 → 4.1, Java 21 → 24/26 base images, a React plugin major that
already failed its checks). They were closed unmerged, and the configuration was changed to
**monthly, grouped (one PR per ecosystem), minor and patch updates only**. Major upgrades are planned
deliberately (Spring Boot 4 is listed under future work). Lesson: automated dependency updates need
policy (grouping, schedule, version limits), or they create more risk and noise than they remove.

### Checkov / Trivy config

Run on every PR (report-only; full results in the *IaC security scan* job logs). Known design choices
in this project that such scanners flag, and why they were kept:

| Choice | Reason | Production alternative |
|---|---|---|
| EKS API endpoint publicly reachable | `kubectl` from a laptop | Restrict `public_access_cidrs`, or private endpoint + VPN |
| No EKS control-plane logging, no KMS envelope encryption of Secrets | Cost and simplicity | Enable both |
| ECR: AES-256 instead of KMS, mutable tags | CI pushes unique commit-SHA tags; re-running a job may push the same tag | KMS key, `IMMUTABLE` tags |
## 3. Accepted risks

Accepted by the project owner only when **no fixed version is available in the current line** and the
**vulnerable feature is not used**. Each entry is in `.trivyignore` with its reason and an **expiry
date**, after which Trivy reports it again.

| CVE | Component | Why not exploitable in VoltCart | Expires | Planned fix |
|---|---|---|---|---|
| CVE-2026-47884 | spring-webmvc 6.2 – RCE via `XsltView` | No XSLT views; all controllers are `@RestController` returning JSON (verified by code search) | 2027-02-28 | Spring Boot 4 / Spring 7 |
| CVE-2026-47890 | spring-webmvc/webflux 6.2 – SSE with views | No Server-Sent Events | 2027-02-28 | Spring Boot 4 / Spring 7 |
| CVE-2026-47892 | spring-webflux 6.2 – header predicate bypass in functional endpoints | Gateway defines no `RouterFunction`/`RequestPredicates`; routing uses Spring Cloud Gateway's `RouteLocator` | 2027-02-28 | Spring Boot 4 / Spring 7 |
| CVE-2026-113425, -121810, -123007, -123633 | nginx 1.30.6 – mail proxy, HTTP/3, HTTP/2 | nginx serves plain HTTP/1.1 on port 8080 only – no `http2`, `quic`, `ssl` or `mail` | **2026-12-31** | nginx-unprivileged 1.30.7 image when published |

## 4. Known gaps and future work

| Gap | Impact | Recommended next step |
|---|---|---|
| **No user authentication** – sign-in is a demo lookup by email; APIs are open behind the gateway | Anyone can call the APIs (e.g. list users) | JWT login in user-service, token validation and route rules at the gateway; NetworkPolicies so only the gateway reaches the services |
| **Test coverage 42.7 %** (SonarCloud gate requires 80 % on new code) | Controllers, DTOs, entities and the React code are untested | Controller tests (`@WebMvcTest`), React component tests, coverage exclusions for generated/config code |
| **HTTP only** | Traffic between browser and load balancer is unencrypted | Route 53 domain + ACM certificate, HTTPS listener, redirect HTTP→HTTPS |
| **Secrets created by hand** | Manual step, no rotation | External Secrets Operator + AWS Secrets Manager (or Sealed Secrets) |
| **Backups in the same cluster** | Lost if the cluster/AZ is lost | Copy `pg_dump` output to S3 (versioned, lifecycle), or use Amazon RDS with automated backups |
| **Spring Boot 3.5 / Spring Framework 6.2** | Some CVEs only fixed in 7.x (accepted above) | Upgrade to Spring Boot 4 |
| **No NetworkPolicies, no admission policies** | Any pod can reach any pod | NetworkPolicies per service; Kyverno/Gatekeeper to enforce the security context |
| **Long-lived IAM user keys on the admin laptop** | Key leakage risk | AWS IAM Identity Center (SSO) / short-lived credentials |
