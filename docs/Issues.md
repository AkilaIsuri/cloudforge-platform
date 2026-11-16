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
