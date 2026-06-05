# Observability

Finance Portal observability stack contains Prometheus, Grafana, Kafka log pipeline, OpenSearch, and OpenSearch Dashboards.

## Run

```powershell
cd infra
docker compose up -d
```

## URLs

- Prometheus: http://localhost:9090
- Prometheus targets: http://localhost:9090/targets
- Grafana: http://localhost:3001
- OpenSearch: http://localhost:9200
- OpenSearch Dashboards: http://localhost:5601
- Tempo traces (Grafana Explore): http://localhost:3001 → Explore → Tempo
- Local backend metrics: http://localhost:8080/actuator/prometheus

## Grafana

- Username: `admin`
- Password: `admin`
- Datasource provisioning: `grafana/provisioning/datasources`
- Dashboard provisioning: `grafana/provisioning/dashboards`
- Dashboard exports: `dashboards`
- Provisioned dashboard: `Finance Portal Overview`

## Prometheus

Development config:

```text
prometheus/prometheus.yml -> host.docker.internal:8080/actuator/prometheus
```

Dockerized backend config:

```text
prometheus/prometheus-docker.yml -> backend:8080/actuator/prometheus
```

Alert rule templates:

```text
prometheus/rules/finance-portal-alerts.yml
```

## Logs

Local backend log directory:

```text
backend/logs
```

Project-root `logs` is not used.

OpenSearch index pattern:

```text
finance-portal-logs-*
```

Detailed usage notes are in:

```text
docs/observability.md
```
