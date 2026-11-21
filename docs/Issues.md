# CloudForge – Issues and Troubleshooting Log

Problems found while building and deploying CloudForge (VoltCart), how they were diagnosed, and how
they were fixed. Each entry follows **Problem / Root cause / Solution**, plus the troubleshooting
steps, so the same kind of issue can be solved faster next time.

| # | Issue | Stage | Area |
|---|---|---|---|
| 1 | [Frontend container reported `unhealthy`](#1-frontend-container-reported-unhealthy) | Docker Compose | Health checks / networking |
| 2 | [403 Forbidden when registering a user on Kubernetes](#2-403-forbidden-when-registering-a-user-on-kubernetes) | Local Kubernetes | API gateway / CORS |
| 3 | [order-service unit tests failed (`mvn test`)](#3-order-service-unit-tests-failed-mvn-test) | Build / tests | Spring test config / Mockito |
| 4 | [Grafana showed a scaled-down service as UP while its alert was firing](#4-grafana-showed-a-scaled-down-service-as-up-while-its-alert-was-firing) | Local Kubernetes | Monitoring / dashboards |
| 5 | [SonarCloud: "Not authorized or organization not found"](#5-sonarcloud-not-authorized-or-organization-not-found) | CI (GitHub Actions) | Security scanning / SonarCloud |
| 6 | [Unable to resolve action `aquasecurity/trivy-action@0.28.0`](#6-unable-to-resolve-action-aquasecuritytrivy-action0280) | CI (GitHub Actions) | Workflow dependencies |
| 7 | [EKS node group `CREATE_FAILED`: instance type not eligible for Free Tier](#7-eks-node-group-create_failed-instance-type-not-eligible-for-free-tier) | AWS / Terraform | EC2 / EKS node group |
| 8 | [Trivy blocked the release: 9 CRITICAL CVEs in the Spring Boot images](#8-trivy-blocked-the-release-9-critical-cves-in-the-spring-boot-images) | CI – security gate | Dependencies / vulnerability management |
| 9 | [Trivy blocked the api-gateway image: Netty, Bouncy Castle, WebFlux CVEs](#9-trivy-blocked-the-api-gateway-image-netty-bouncy-castle-webflux-cves) | CI – security gate | Dependencies / vulnerability management |
| 10 | [Trivy blocked the frontend image: nginx and brotli in the base image](#10-trivy-blocked-the-frontend-image-nginx-and-brotli-in-the-base-image) | CI – security gate | Base images / OS packages |

---

## 1. Frontend container reported `unhealthy`

**Stage:** Docker Compose (local)

### Problem
`docker compose ps` showed the `frontend` container as **unhealthy**, while the other six containers
were healthy. The shop still loaded fine in the browser at `http://localhost:3000`.

### Root cause
The health check ran `wget http://localhost:8080/healthz` **inside** the container.
In the Alpine-based nginx image, `localhost` resolves to the **IPv6** address `::1` first, but nginx
was configured with `listen 8080;`, which listens on **IPv4 only**. The check got
`Connection refused`, while the browser worked because Docker forwards host traffic over IPv4.

### Solution
- `applications/frontend/nginx/default.conf.template`: nginx also listens on IPv6 (`listen [::]:8080;`).
- `docker-compose.yml`: the health check calls `http://127.0.0.1:8080/healthz` explicitly.
- Rebuilt and restarted only the frontend:
  `docker compose up --build -d --no-deps --wait frontend`

Kubernetes is not affected: its probes call the pod's IP address rather than `localhost`.

### How it was diagnosed
```bash
# 1. Which container is unhealthy?
docker compose ps

# 2. What does the health check itself report? (exit code + output of the last checks)
docker inspect <container> --format '{{range .State.Health.Log}}{{.ExitCode}} {{.Output}}{{end}}'
#    -> "wget: can't connect to remote host: Connection refused"

# 3. Run the check by hand inside the container, with IPv4 instead of "localhost"
docker exec <container> wget -qO- http://127.0.0.1:8080/healthz      # -> ok
docker exec <container> getent hosts localhost                       # -> ::1 localhost
```
**Lesson:** "unhealthy" does not always mean the app is broken – check whether the *health check*
is wrong. Reproduce the check by hand inside the container.

---

## 2. 403 Forbidden when registering a user on Kubernetes

**Stage:** Local Kubernetes (Docker Desktop, Helm chart `helm/cloudforge`)

### Problem
After deploying VoltCart to Kubernetes, the product list loaded, but **registering a user failed
with HTTP 403 Forbidden**. The same action worked in Docker Compose.

### Root cause
The API gateway has a **CORS** (Cross-Origin Resource Sharing) filter that only accepts requests
from an allow-list of browser origins, set by the `CORS_ALLOWED_ORIGINS` environment variable.
Its default allowed only `http://localhost:5173` (Vite dev server) and `http://localhost:3000`
(Docker Compose).

- A browser adds an `Origin` header to POST requests (register, checkout). Simple same-site GET
  requests usually do not carry it, which is why browsing products still worked.
- nginx forwards that header to the gateway.
- On Kubernetes, VoltCart is served from `http://localhost:30080` (NodePort) or
  `http://localhost:8088` (port-forward). That origin was **not on the allow-list**, so the
  gateway's CORS filter rejected the request with **403** before it reached user-service.

### Solution
Made the allowed origins **per-environment configuration** in Helm instead of a fixed default.

`helm/cloudforge/values-local.yaml`:
```yaml
services:
  api-gateway:
    env:
      CORS_ALLOWED_ORIGINS: "http://localhost:30080,http://localhost:8088,http://localhost:5173"
```
Applied with a config-only rollout (no image rebuild):
```bash
helm upgrade --install cloudforge helm/cloudforge -n cloudforge -f helm/cloudforge/values-local.yaml
```
Kubernetes performed a **rolling update** of `api-gateway` only; the other pods were untouched.

> **For EKS:** `values-eks.yaml` must list the public URL of the load balancer / domain in
> `CORS_ALLOWED_ORIGINS`, or the same 403 will happen in the cloud.

### How it was diagnosed
```bash
# 1. Reproduce without the browser: send the same Origin header the browser sends
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Origin: http://localhost:30080" http://localhost:30080/api/products     # -> 403

# 2. Change only the Origin to one that is allowed
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Origin: http://localhost:3000" http://localhost:30080/api/products      # -> 200
#    Same request, different Origin, different result => CORS allow-list

# 3. Confirm the fix reached the running pod
kubectl exec deploy/api-gateway -n cloudforge -- printenv CORS_ALLOWED_ORIGINS
helm history cloudforge -n cloudforge        # new revision listed
```
**Lesson:** when something works in one environment and fails in another, look for what is
*different* between them – here, the URL (origin) the app is served from.

---

## 3. order-service unit tests failed (`mvn test`)

**Stage:** Build / tests (Maven in Docker)

### Problem
Running the order-service tests failed with 2 of 5 tests broken:
```bash
docker run --rm -v "$PWD/applications/order-service":/app -w /app \
  maven:3.9.9-eclipse-temurin-21 mvn -q test
```
- `OrderServiceApplicationTests.contextLoads` – `Could not resolve placeholder 'services.payment-url'`
- `OrderServiceTest.placeOrder_rollsBackEarlierItems_whenLaterItemOutOfStock` – expected
  `ResponseStatusException` but got Mockito's `PotentialStubbingProblem`

The Docker images still built, because the Dockerfiles run `mvn package -DskipTests`.

### Root cause
1. **Test config shadows the main config.** `src/test/resources/application.properties` has the
   *same file name* as `src/main/resources/application.properties`. On the test classpath the test
   file **replaces** the main one instead of adding to it, so `services.user-url`,
   `services.product-url` and `services.payment-url` did not exist, and the `@Value("${services…}")`
   client beans could not be created. payment-service had the same gap for
   `payment.max-approved-amount`.
2. **Incomplete mock in the rollback test.** The test stubbed `productClient.reserve(20L, 5)` to throw
   409, but the code first calls `reserve(10L, 1)` for the first item. Mockito's strict stubs treat
   a call with *different arguments* than any stub as a likely mistake and throw
   `PotentialStubbingProblem` before the code under test can react.

### Solution
- `order-service/src/test/resources/application.properties`: added `services.user-url`,
  `services.product-url`, `services.payment-url`.
- `payment-service/src/test/resources/application.properties`: added `payment.max-approved-amount=5000`.
- `OrderServiceTest`: stub the first, successful reservation explicitly:
  `doNothing().when(productClient).reserve(10L, 1);`

Result: all four Spring services pass `mvn test` (user 1, product 4, order 5, payment 3 tests).

### How it was diagnosed
```bash
# 1. Read the summary at the end of the Maven output: which tests failed, and the first "Caused by"
grep -h "Caused by" applications/order-service/target/surefire-reports/*.txt | sort -u

# 2. Find where the missing property is defined and used
grep -rn "services\." applications/order-service/src/main/resources/application.properties
grep -rn '\${services' applications/order-service/src/main/java
```
**Lesson:** a `src/test/resources/application.properties` overrides the *whole* main file. Every
property the app needs without a default must be repeated there (or use a separate
`application-test.properties` with `@ActiveProfiles("test")`). And run `mvn test` regularly – a
`-DskipTests` image build hides broken tests.

---

## 4. Grafana showed a scaled-down service as UP while its alert was firing

**Stage:** Local Kubernetes – alert demo (`kubectl scale deploy/product-service --replicas=0`)

### Problem
After scaling `product-service` to 0 replicas, Prometheus fired `CloudForgeServiceUnavailable`
correctly, but the **"Service status"** panel on the *VoltCart – Service Overview* dashboard still
showed product-service as **UP**.

### Root cause
The panel and the alert used **different signals**:
- The panel used `up{namespace="cloudforge"}`. `up` is written only when Prometheus *tries* to scrape
  a target: `1` = success, `0` = failure. With 0 replicas there is **no pod and no target**, so
  Prometheus writes nothing – the series simply stops (it does not become 0).
- The stat panel showed the *last non-null value in the time range*, which was the old `1` → "UP".
- The alert used `kube_deployment_status_replicas_available` from kube-state-metrics, which keeps
  reporting the Deployment and correctly showed **0 available pods**.

### Solution
Changed the panel (`helm/cloudforge/dashboards/voltcart-overview.json`) to the same signal as the
alerts, evaluated at the current time only (`"instant": true`):
```promql
max by (deployment)  (kube_deployment_status_replicas_available{namespace="cloudforge"})
max by (statefulset) (kube_statefulset_status_replicas_ready{namespace="cloudforge"})
```
Value mapping: `0` → **DOWN** (red), `1+` → **UP** (green). The panel now also covers the frontend
and PostgreSQL. Rolled out with `helm upgrade`; Grafana's sidecar reloads the dashboard ConfigMap
automatically.

### How it was diagnosed
```promql
up{namespace="cloudforge", job="product-service"}                                   # no result after scale-down
kube_deployment_status_replicas_available{namespace="cloudforge", deployment="product-service"}   # 0
```
**Lesson:** "the series disappeared" and "the value is 0" are different things in Prometheus. For
"is it running?" use the orchestrator's view (kube-state-metrics) or `absent()`, not only `up`. Keep
dashboards and alerts on the **same signal** so they never disagree.

---

## 5. SonarCloud: "Not authorized or organization not found"

**Stage:** CI – first pull request run (`SonarCloud analysis` job)

### Problem
The SonarCloud job failed after 5 seconds:
```
ERROR Fail to load the list of plugins installed on the server: Not authorized or organization not found
Error: Process completed with exit code 3.
```

### Root cause
The workflow used `-Dsonar.organization=AkilaIsuri` and `-Dsonar.projectKey=cloudforge-platform`.
Those were the **display names**, not the **keys**. SonarCloud keys are case-sensitive; when an
organization is imported from GitHub its key is lowercased, and the project key is
`<GitHubOwner>_<repository>`:
- organization key: `akilaisuri`
- project key: `AkilaIsuri_cloudforge-platform`

(The same error also appears when the `SONAR_TOKEN` secret is missing or invalid, so both had to be
ruled out.)

### Solution
Set the real keys in `.github/workflows/ci-cd.yml`. The first fix still failed with the same error,
because **GitHub Actions repository variables** `SONAR_ORGANIZATION` / `SONAR_PROJECT_KEY` had been
created with the display names. The workflow uses `${{ vars.SONAR_ORGANIZATION || 'akilaisuri' }}`,
so a variable, when present, **overrides** the default in the file. Correcting the variables
(Settings → Secrets and variables → Actions → Variables) and re-running the job fixed it – the log
line `+ sonar-scanner -Dsonar.organization=…` showed which value was really used.

### How it was diagnosed
SonarCloud's public API shows which keys exist (no login needed for public projects):
```bash
curl -s "https://sonarcloud.io/api/organizations/search?organizations=akilaisuri"      # found
curl -s "https://sonarcloud.io/api/organizations/search?organizations=AkilaIsuri"      # not found
curl -s "https://sonarcloud.io/api/components/show?component=AkilaIsuri_cloudforge-platform"  # found
```
**Lesson:** use the *key* fields from SonarCloud's **Information** page, not the names shown in the
UI. When an error says "A or B", rule out each cause separately.

---

## 6. Unable to resolve action `aquasecurity/trivy-action@0.28.0`

**Stage:** CI – the Trivy jobs failed before any step ran ("Prepare all required actions")

### Problem
```
Error: Unable to resolve action `aquasecurity/trivy-action@0.28.0`, unable to find version `0.28.0`
```

### Root cause
The action's maintainers renamed their release tags to use a `v` prefix (`v0.28.0`, …). A workflow
pins an action to a **Git tag**; when that tag no longer exists, GitHub cannot download the action.

### Solution
Pinned to the current release `aquasecurity/trivy-action@v0.36.0` in `ci-cd.yml` and
`iac-security.yml`, and checked that every other `uses:` reference resolves.

### How it was diagnosed
```bash
# Which tags exist?
curl -s "https://api.github.com/repos/aquasecurity/trivy-action/tags?per_page=15"
# Does a specific ref exist? (200 = yes, 404 = no)
curl -s -o /dev/null -w '%{http_code}\n' \
  https://api.github.com/repos/aquasecurity/trivy-action/git/ref/tags/v0.36.0
```
**Lesson:** pinned versions can disappear. Check action tags on GitHub when pinning. For stronger
supply-chain security, pin third-party actions to a full **commit SHA** (immutable) and let
Dependabot propose updates.

---

## 7. EKS node group `CREATE_FAILED`: instance type not eligible for Free Tier

**Stage:** AWS – first `terraform apply` (VPC, ECR, IAM and the EKS control plane were created; the
node group failed after ~15 minutes)

### Problem
```
Error: waiting for EKS Node Group (cloudforge-eks:cloudforge-eks-nodes) create: unexpected state
'CREATE_FAILED', wanted target 'ACTIVE'. last error: ... AsgInstanceLaunchFailures: Could not launch
On-Demand Instances. InvalidParameterCombination - The specified instance type is not eligible for
Free Tier.
```

### Root cause
The AWS account is on the **Free Tier plan**, which only allows launching *free-tier-eligible* EC2
instance types. The node group requested `t3.large`, which is not eligible, so its Auto Scaling
group could not start any instance and EKS marked the node group `CREATE_FAILED`.
`terraform plan` cannot detect this – the request is valid; only the account's plan rejects it.

### Solution
Changed the default node type in `terraform/variables.tf` to **`m7i-flex.large`** – free-tier
eligible, same size as `t3.large` (2 vCPU, 8 GB), x86 (runs the amd64 images CI builds), up to 29
pods per node. Re-ran `terraform plan` / `terraform apply`: Terraform had marked the failed node group
as *tainted*, so it replaced only that resource and created the remaining add-ons.

### How it was diagnosed
```bash
# Which instance types may this account launch?
aws ec2 describe-instance-types --region us-east-1 \
  --filters Name=free-tier-eligible,Values=true \
  --query 'InstanceTypes[].[InstanceType,VCpuInfo.DefaultVCpus,MemoryInfo.SizeInMiB]' --output text
```
Max pods per node on EKS (AWS VPC CNI) = `ENIs × (IPs per ENI − 1) + 2` → m7i-flex.large:
`3 × (10 − 1) + 2 = 29`.

**Lesson:** the error said exactly what was wrong – read the *last error* at the end of a long
Terraform message. Account-level restrictions (Free Tier plan, service quotas, SCPs) only show up at
apply time, so check them before choosing instance sizes.

---

## 8. Trivy blocked the release: 9 CRITICAL CVEs in the Spring Boot images

**Stage:** CI – first run on `main` after merging PR #1 (`Image <service>` jobs, Trivy step)

### Problem
The security gate (Trivy, `severity: CRITICAL`, `ignore-unfixed: true`, `exit-code: 1`) failed the
image jobs of the four Tomcat-based services, so no image was pushed to ECR:
```
Java (jar)  Total: 9 (CRITICAL: 9)
org.apache.tomcat.embed:tomcat-embed-core 10.1.43  – 7 CVEs, fixed in 10.1.55 … 10.1.58
org.springframework:spring-webmvc 6.2.9            – CVE-2026-47884, CVE-2026-47890, fixed in 7.0.9
```
This is the gate **working as intended**: vulnerable images never reached the registry.

### Root cause
The services were built on Spring Boot **3.5.4**, which bundles Tomcat 10.1.43 and Spring
Framework 6.2.9. Vulnerabilities published since then have fixes in newer patch releases – except two
Spring Framework CVEs that are only fixed in the 7.0 line (Spring Boot 4).

### Solution
1. **Patched dependencies** (all 5 services):
   - Spring Boot `3.5.4` → `3.5.16` (latest 3.5.x), Spring Cloud `2025.0.0` → `2025.0.3` (gateway)
   - Embedded Tomcat pinned to `10.1.62` via `<tomcat.version>` (user, product, order, payment)
   - Frontend base image `nginxinc/nginx-unprivileged:1.27-alpine` → `1.30.6-alpine`
2. **Documented risk acceptance** (decision of the project owner) for the two Spring Framework CVEs
   that have no 6.2.x fix, in `.trivyignore`, referenced explicitly in `ci-cd.yml`:

   | CVE | Issue | Why not exploitable in VoltCart | Expires |
   |---|---|---|---|
   | CVE-2026-47884 | RCE via `XsltView` path handling | No XSLT views are used | 2027-02-28 |
   | CVE-2026-47890 | Stream corruption in Server-Sent Events with views | No SSE; all endpoints return JSON from `@RestController` | 2027-02-28 |

   The expiry date makes Trivy report them again, forcing a re-review. The gate stays active for
   every other CRITICAL vulnerability. **Follow-up:** upgrade to Spring Boot 4 / Spring Framework 7.

### How it was diagnosed
```bash
# Read the Trivy table in the failed job: library, installed version, fixed version
# Find the newest available versions on Maven Central
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml
curl -s https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-core/maven-metadata.xml
curl -s https://repo1.maven.org/maven2/org/springframework/spring-webmvc/maven-metadata.xml
```
**Lesson:** a scanner in the pipeline only helps if it can *stop* a release. When it does: patch what
has a fix; for what does not, decide explicitly – fix by a major upgrade, or accept the risk with a
written reason, evidence that the vulnerable code path is unused, and an expiry date. Never just turn
the gate off.

---

## 9. Trivy blocked the api-gateway image: Netty, Bouncy Castle, WebFlux CVEs

**Stage:** CI – run on `main` after merging PR #2 (`Image api-gateway` job, after the fixes from #8).

### Problem
```
Java (jar)  Total: 3 (CRITICAL: 3)
io.netty:netty-handler 4.1.135.Final          CVE-2026-75595  fixed in 4.1.137.Final
org.bouncycastle:bcprov-jdk18on 1.80.2         CVE-2026-8763   fixed in 1.85
org.springframework:spring-webflux 6.2.19      CVE-2026-47892  fixed in 7.0.9
```

### Root cause
The gateway is reactive (Spring Cloud Gateway on WebFlux/Netty), so it ships a different set of
libraries from the Tomcat-based services. Spring Boot 3.5.16 still manages Netty 4.1.135, and
Bouncy Castle arrives transitively through Spring Cloud. As in #8, the Spring Framework CVE is only
fixed in the 7.0 line.

### Solution
- `api-gateway/pom.xml`: `<netty.version>4.1.141.Final</netty.version>` (Spring Boot's managed
  Netty version) and a `dependencyManagement` override forcing Bouncy Castle `bcprov` / `bcpkix` /
  `bcutil` to **1.87**.
- **Risk acceptance (decision of the project owner, 2026-11-21)** for **CVE-2026-47892** in
  `.trivyignore`, expiry 2027-02-28: the bypass affects WebFlux *functional endpoints*
  (`RouterFunction` + `RequestPredicates`). The gateway defines none – a code search for
  `RouterFunction|RequestPredicates|HandlerFunction` returns nothing; routes are configured with
  Spring Cloud Gateway's `RouteLocator`. Follow-up: Spring Boot 4.

### How it was diagnosed
```bash
# Fixed versions available?
curl -s https://repo1.maven.org/maven2/io/netty/netty-handler/maven-metadata.xml
curl -s https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/maven-metadata.xml
# Is the vulnerable feature used?
grep -rnE 'RouterFunction|RequestPredicates|HandlerFunction' applications/api-gateway/src
```
**Lesson:** each image has its own dependency tree – scan and patch every image, not just one
representative. Transitive dependencies (Bouncy Castle here) can be forced to a safe version with
`dependencyManagement` without touching the library that pulls them in.

---

## 10. Trivy blocked the frontend image: nginx and brotli in the base image

**Stage:** CI – same run as #9 (`Image frontend` job)

### Problem
```
frontend:<sha> (alpine 3.24.2)   Total: 5 (CRITICAL: 5)
brotli-libs 1.2.0-r1   CVE-2026-111497                                         fixed in 1.2.1-r0
nginx 1.30.6-r1        CVE-2026-113425 (mail proxy), CVE-2026-121810 (HTTP/3),
                       CVE-2026-123007, CVE-2026-123633 (HTTP/2)              fixed in 1.30.7-r0
```

### Root cause
The vulnerabilities are in **OS packages of the base image** (`nginxinc/nginx-unprivileged:1.30.6-alpine`),
not in our code. Alpine already ships the fixes, but the newest published base image (built
2026-10-19) still contains the old packages.

### Solution
- **brotli-libs:** patched in our Dockerfile with `apk upgrade --no-cache brotli-libs` (as root, then
  back to the non-root user `101`). Only this library is upgraded – running `apk upgrade nginx` would
  replace the nginx.org build with Alpine's own nginx package (different config layout and user).
- **nginx (4 CVEs): risk acceptance with a short expiry (2026-12-31)**, decision of the project owner.
  All four need HTTP/2, HTTP/3/QUIC or the mail module. VoltCart's nginx only has
  `listen 8080; listen [::]:8080;` – plain HTTP/1.1, no `ssl`, `http2`, `quic` or `mail`
  (TLS / HTTP/2 to the internet terminate at the AWS load balancer). Follow-up: bump the base image to
  `1.30.7-alpine` as soon as it is published, then remove the entries.

### How it was diagnosed
```bash
# Is a newer base image published?
curl -s "https://hub.docker.com/v2/repositories/nginxinc/nginx-unprivileged/tags?page_size=100&ordering=last_updated"
# Are the vulnerable features enabled?
grep -niE 'http2|http3|quic|mail|ssl' applications/frontend/nginx/*     # → none
```
**Lesson:** base-image CVEs often have fixes in the distribution before the image is rebuilt. Patch
what is safe to patch, accept the rest only with evidence and a *short* expiry, and track the
upstream release.

---

## How to troubleshoot an HTTP error like this (general method)

A request from the browser passes through several layers. Find **which layer** returns the error,
then look only at that layer.

```
Browser ──► frontend (nginx) ──► api-gateway ──► service (e.g. user-service) ──► PostgreSQL
            /api/* proxy         routes, CORS     business logic                   data
```

### Step 1 – Read the error in the browser
Open **DevTools → Network** (F12), click the failed request and note:
- **Status code** (403, 404, 500, 502, 503 …) and **request URL / method**
- **Request headers** (e.g. `Origin`, `Content-Type`) and the **response body**
- The **Console** tab: CORS problems often show a message mentioning "CORS" or "origin".

### Step 2 – Reproduce it with `curl`, removing the browser
Copy the request into `curl` (DevTools: right-click → *Copy as cURL*). If `curl` gets the same
error, the problem is on the server side. Then change **one thing at a time** – a header, the URL,
the body – until the result changes. The thing you changed is the cause.

### Step 3 – Go one layer deeper and test each layer directly
```bash
# Through the frontend's nginx (what the browser uses)
curl -i http://localhost:30080/api/users

# Directly to the gateway (skips nginx)
kubectl port-forward svc/api-gateway -n cloudforge 9080:8080
curl -i http://localhost:9080/api/users

# Directly to the service (skips the gateway)
kubectl port-forward svc/user-service -n cloudforge 9081:8080
curl -i http://localhost:9081/users
```
The first layer that returns the error is the one to fix. In issue 2, the request worked directly
against user-service, but failed at the gateway → the gateway was the cause.

### Step 4 – Read the logs of that layer
```bash
kubectl get pods -n cloudforge                       # is the pod Running and READY 1/1? restarts?
kubectl logs deploy/api-gateway -n cloudforge --tail=100
kubectl logs deploy/user-service -n cloudforge --tail=100
kubectl describe pod <pod-name> -n cloudforge        # Events: probe failures, image or volume errors
```
(Docker Compose equivalents: `docker compose ps`, `docker compose logs <service> --tail=100`.)

### Step 5 – Check the configuration the pod is actually running with
Many "works here, fails there" problems are configuration, not code.
```bash
kubectl exec deploy/api-gateway -n cloudforge -- printenv | sort    # environment variables
helm get values cloudforge -n cloudforge                            # values Helm used
helm history cloudforge -n cloudforge                               # what changed, and when
```

### Step 6 – Fix, roll out, verify
- Fix it in **Git-tracked files** (Helm values, code), not by editing live resources by hand.
- Roll out (`helm upgrade …` locally; later Argo CD on EKS) and **re-run the exact `curl`** from
  Step 2 to prove it is fixed.
- Add the issue to this file.

### Quick reference: what the status code usually means here

| Code | Usually means | Where to look first |
|---|---|---|
| **400** Bad Request | Request body failed validation (`@Valid`), e.g. missing field | Response body message; the service's controller/DTO |
| **403** Forbidden | Rejected before the app logic – in this project, the gateway's **CORS** allow-list (later also missing permissions/JWT) | Gateway config: `CORS_ALLOWED_ORIGINS`; compare the `Origin` header |
| **404** Not Found | Wrong path, or no gateway route for it, or the record does not exist | Gateway routes (`RouteConfig`), service `@RequestMapping` |
| **409** Conflict | Business rule, e.g. not enough stock | Response body message; product-service |
| **500** Internal Server Error | Exception in the service | `kubectl logs` of that service (stack trace) |
| **502 / 503 / 504** | The next layer is down, not ready, or too slow | `kubectl get pods` (READY, RESTARTS), readiness probes, Service names/ports |
