# Observability Faz 2 ve 3

Local development icin Prometheus, Grafana, OpenSearch, OpenSearch Dashboards ve log shipper kurulumu `infra/docker-compose.yml` icinden yonetilir.

## Calistirma

```powershell
cd infra
docker compose up -d
```

## Adresler

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001
- Backend metrics: http://localhost:8080/actuator/prometheus
- OpenSearch: http://localhost:9200
- OpenSearch Dashboards: http://localhost:5601

## Giris Bilgileri

- Grafana kullanici adi: `admin`
- Grafana sifre: `admin`

## Notlar

- Prometheus, backend container icindeki `backend:8080/actuator/prometheus` endpointini scrape eder.
- Grafana datasource provisioning ile Prometheus'u otomatik ekler.
- Dashboard provisioning ile `Finance Portal Backend` dashboard'u otomatik yuklenir.
- Backend JSON log dosyasi host uzerinde `backend/logs/finance-portal/finance-portal-backend.json` olarak yazilir.
- Fluent Bit bu JSON log dosyasini okuyup OpenSearch'e aktarir.
- OpenSearch tarafinda log index pattern'i `finance-portal-logs-*` olarak kullanilabilir.
- OpenSearch Dashboards operasyonel log gorunumleri icin `infra/observability/opensearch-dashboards/README.md` ve `queries.md` dosyalarini kullan.

## OpenSearch Sorun Giderme

OpenSearch container konfig degisikligi sonrasi temiz yeniden baslatma icin:

```powershell
docker compose down -v
docker compose up -d
docker ps
curl http://localhost:9200
```

## OpenSearch Dashboards Operasyonel Kullanim

- `ERROR Logs`: exception ve kritik hata takibi
- `WARN + ERROR`: anormal durumlarin erken fark edilmesi
- `HTTP 404 / 5xx Tracking`: hatali endpoint ve backend exception analizi
- `Slow Requests`: yavas endpointleri bulma
- `Provider Failures`: BINANCE, EVDS, TEFAS ve benzeri provider problemlerini izleme
- `requestId Search Flow`: tek request'in access log, business log ve exception akislarini bir arada inceleme

Hazir sorgular:
- `infra/observability/opensearch-dashboards/queries.md`
