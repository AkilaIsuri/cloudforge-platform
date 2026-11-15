# Issues Log

Problems found while building and running CloudForge, and how they were fixed.

---

## Frontend container reported `unhealthy` in Docker Compose

**Problem**
`docker ps` showed `cloudforge-platform-frontend-1` as `(unhealthy)`, even though the
shop loaded fine on http://localhost:3000. The health check log showed:
`wget: can't connect to remote host: Connection refused`.

**Root cause**
The Compose health check called `wget http://localhost:8080/healthz`. Inside the Alpine
container, `localhost` resolves to the IPv6 address `::1` first, but nginx was configured with
`listen 8080;`, which binds IPv4 only. The health check therefore hit an address nginx was not
listening on. `127.0.0.1:8080/healthz` returned `ok`.

**Solution**
Changed the health check in `docker-compose.yml` to use the IPv4 address explicitly:

```yaml
test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:8080/healthz || exit 1"]
```

Recreated the container with `docker compose up -d frontend`; it now reports `healthy`.
Kubernetes is not affected, because its probes call the pod IP rather than `localhost`.
