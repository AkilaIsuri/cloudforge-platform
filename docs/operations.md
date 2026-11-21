# CloudForge – Operations

How VoltCart is monitored, alerted on, backed up and kept running. Commands assume the kubectl
context of the target cluster (`kubectl config current-context`) and namespace `cloudforge`.

## Monitoring

**Stack:** kube-prometheus-stack (Prometheus, Grafana, Alertmanager, kube-state-metrics, node-exporter),
Helm release `monitoring` in namespace `monitoring`. Opening the UIs: see the
[deployment guide](deployment-guide.md#35-uis-all-through-port-forward--nothing-except-voltcart-is-public).

### What is measured

| Source | Metrics |
|---|---|
| Spring Boot Actuator / Micrometer (`/actuator/prometheus`, all 5 services) | `http_server_requests_seconds_*` (count, sum, **histogram buckets** for p95), JVM memory/GC/threads, HikariCP pool |
| order-service (business) | `voltcart_orders_total{status="PAID"\|"PAYMENT_FAILED"}`, `voltcart_revenue_total` |
| kube-state-metrics | Deployment/StatefulSet replicas, pod restarts |
| cAdvisor / node-exporter | Container CPU and memory, node resources |

Prometheus finds the services through the chart's **ServiceMonitor** `cloudforge-services` (label
`release: monitoring`, selects Services labelled `cloudforge.io/metrics: "true"`, scrape every 30 s).

### Grafana dashboard – *VoltCart – Service Overview*

Provisioned as code: `helm/cloudforge/dashboards/voltcart-overview.json`, loaded by Grafana's sidecar
from the ConfigMap `voltcart-dashboard` (label `grafana_dashboard: "1"`).

| Panel | Query basis |
|---|---|
| Service status (UP/DOWN per component, incl. frontend and Postgres) | Available replicas from kube-state-metrics – same signal as the alerts ([Issues #4](Issues.md#4-grafana-showed-a-scaled-down-service-as-up-while-its-alert-was-firing)) |
| Orders paid · Payments failed · Revenue | `increase(voltcart_*_total[$__range])` |
| Pod restarts (1h) | `kube_pod_container_status_restarts_total` |
| Request rate by service | `rate(http_server_requests_seconds_count)` excluding `/actuator` |
| Error rate (5xx) by service | 5xx share of all requests (red line at 5 %) |
| p95 latency by service | `histogram_quantile(0.95, …_bucket)` (red line at 1 s) |
| Orders per minute by status | paid vs failed |
| JVM heap · CPU by pod | resource usage |

### Alerts – PrometheusRule `cloudforge-alerts`

| Alert | Condition | For | Severity |
|---|---|---|---|
| CloudForgeServiceUnavailable | a Deployment has 0 available replicas | 1 m | critical |
| CloudForgePostgresDown | the postgres StatefulSet has 0 ready replicas | 1 m | critical |
| CloudForgeMetricsTargetDown | Prometheus cannot scrape a service (`up == 0`) | 2 m | warning |
| CloudForgePodCrashLooping | > 2 container restarts in 10 m | 1 m | warning |
| CloudForgeHighErrorRate | > 5 % of a service's requests return 5xx | 5 m | critical |
| CloudForgeHighLatency | p95 > 1 s | 5 m | warning |
| CloudForgeManyPaymentFailures | > 5 failed payments in 15 m | – | warning |

Check rule state in Prometheus under **Alerts** (the *Status → Rule health* page only shows whether a
rule evaluates without errors). Alertmanager receives firing alerts; a Slack/e-mail receiver would be
configured in `helm/monitoring/values.yaml`.

## Backup and recovery

| Resource | Behaviour |
|---|---|
| PVC `data-postgres-0` | Database files (10 Gi gp3 on EKS); survives pod restarts and rescheduling |
| PVC `postgres-backups` | Separate disk for dumps; kept on `helm uninstall` |
| CronJob `postgres-backup` | Daily 02:00 UTC: `pg_dump --format=custom` of all 4 databases into `/backups/<UTC timestamp>/`, keeps the newest 7 |
| CronJob `postgres-restore` | Suspended template; restores the newest backup (or `postgres.backup.restoreBackupId`) with `pg_restore --clean` |

### Runbook

```bash
# Back up now
kubectl create job -n cloudforge --from=cronjob/postgres-backup backup-$(date +%s)
kubectl logs -n cloudforge job/<job-name> -f              # "Backup <timestamp> complete" + 4 dumps

# Restore the newest backup (replaces current data in all 4 databases)
kubectl create job -n cloudforge --from=cronjob/postgres-restore restore-$(date +%s)
kubectl logs -n cloudforge job/<job-name> -f              # "Restore of <timestamp> complete."

# Verify
kubectl exec -n cloudforge postgres-0 -- psql -U cloudforge -d orders_db -c "SELECT count(*) FROM orders;"
```

**Tested:** orders were deleted (`DELETE FROM order_items; DELETE FROM orders;`) and fully recovered
with the restore job; a backup was also taken on EKS.

**Limits:** recovery point = last backup (up to 24 h); backups share the cluster – copy them to S3 for
disaster recovery (see [security.md](security.md#4-known-gaps-and-future-work)).

## Self-healing demonstrations

Run with `kubectl get pods -n cloudforge -w` in a second terminal and the Argo CD UI open.

| # | Action | Expected result | Mechanism |
|---|---|---|---|
| 1 | `kubectl delete pod -n cloudforge -l app=product-service` | New pods (new names) start immediately; shop keeps working once Ready | ReplicaSet keeps 2 replicas |
| 2 | `kubectl exec -n cloudforge deploy/order-service -- kill 1` | Same pod, `RESTARTS` +1 | kubelet restarts the crashed container |
| 3 | `kubectl scale deploy/product-service -n cloudforge --replicas=0` | Argo CD shows OutOfSync briefly, then restores 2 replicas | **Argo CD self-heal** – Git says 2 |
| 4 | Delete `postgres-0` | Same pod name, same PVC, data intact | StatefulSet + PersistentVolume |
| 5 | Deploy a non-existent image tag | New pod `ImagePullBackOff`, old pods keep serving; fix via `git revert` (or `helm rollback` locally) | Rolling update + readiness probe |
| 6 | Place 6 orders over $5,000 within 15 minutes | Grafana *Payments failed* rises; `CloudForgeManyPaymentFailures` fires, then resolves after 15 minutes | Business metric + PrometheusRule |

Probes behind this (`templates/spring-services.yaml`): **startup** probe allows up to 3 minutes for
Spring Boot to start; **liveness** restarts a hung container; **readiness** removes a pod from the
Service until it can serve traffic.
