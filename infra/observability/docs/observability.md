# Finance Portal Observability

## Hızlı Özet — Karşılanan İsterler

| İster | Durum |
|-------|-------|
| Log4j2 | ✅ log4j2-spring.xml + JSON template |
| JSON format (timestamp/level/service/logger/thread/message/exception/traceId/spanId/requestId/userId) | ✅ 48+ alan |
| INFO/WARN/ERROR/DEBUG seviyeleri | ✅ |
| OpenSearch log aktarımı (Fluent Bit) | ✅ |
| Kafka opsiyonu | 📋 belgelenmiş (aşağıya bkz.) |
| OpenTelemetry entegrasyonu | ✅ micrometer-tracing-bridge-otel, sampling %100 |
| Temel metrikler (request count, response time, error rate, JVM, DB pool) | ✅ Actuator + Micrometer + Prometheus |
| Trace/span log correlation | ✅ traceId/spanId MDC'de |
| Prometheus + Grafana | ✅ provisioned, 65KB dashboard |
| OpenSearch log arama | ✅ index template + 20+ örnek sorgu |
| Dashboard'lar (Response Time, Error Rate, Request Volume, Service Health) | ✅ finance-portal-overview.json |
| Güvenlik olayları (401/403, admin ops) | ✅ log_type=security_event + audit.log |
| Scheduler logları | ✅ SchedulerLogSupport, log_type=scheduler |
| Harici servis izleme | ✅ ExternalProviderLogInterceptor |
| Hassas veri maskeleme | ✅ SensitiveDataMasker |

Faz 1 observability setup'i Prometheus, Grafana, Fluent Bit, OpenSearch ve OpenSearch Dashboards bilesenlerinden olusur.

## Servis URL'leri

- Backend metrics: http://localhost:8080/actuator/prometheus
- Prometheus: http://localhost:9090
- Prometheus targets: http://localhost:9090/targets
- Grafana: http://localhost:3001
- OpenSearch: http://localhost:9200
- OpenSearch Dashboards: http://localhost:5601

Grafana varsayilan development bilgileri:

```text
admin / admin
```

Mevcut `grafana_data` volume daha once farkli sifreyle olustuysa bu bilgi gecersiz olabilir.

## Development vs Dockerized Backend

Development ortaminda backend Windows host uzerinde calisir, observability stack Docker icindedir. Bu nedenle Prometheus target:

```text
host.docker.internal:8080
```

`host.docker.internal`, Docker container icinden host makinedeki backend'e erismek icin kullanilir.

Full dockerized ortamda backend de Docker network icinde calisir. Bu senaryoda target:

```text
backend:8080
```

Dosyalar:

- Development: `infra/observability/prometheus/prometheus.yml`
- Dockerized/prod taslak: `infra/observability/prometheus/prometheus-docker.yml`

## Log Path Standardi

Local development icin tek aktif log klasoru:

```text
backend/logs/
```

Backend proje kokunden calistirilirsa resolver otomatik olarak `backend/logs` kullanir. Backend module dizininden calistirilirsa ayni fiziksel klasor `logs` olarak gorunur:

```text
Finans-Portal/backend/logs/
```

`Finans-Portal/logs/` kullanilmamalidir.

Override gereken durumlarda:

```powershell
$env:LOG_PATH="C:\path\to\logs"
```

Docker ortaminda backend container path'i:

```text
/var/log/finance-portal
```

Host mount:

```text
../backend/logs:/var/log/finance-portal
```

Fluent Bit de ayni container path'inden okur:

```text
/var/log/finance-portal/*.log
```

## Grafana Dashboard

Provisioned dashboard:

```text
Finance Portal Overview
```

Dashboard export:

```text
infra/observability/dashboards/finance-portal-overview.json
```

Datasource provisioning:

```text
infra/observability/grafana/provisioning/datasources/prometheus.yml
```

Datasource adi:

```text
Prometheus
```

Dashboard notlari:

- `Backend Status` paneli tek 0/1 state gosterir.
- 4xx ve 5xx panellerinde veri yoksa `0` gosterilir.
- Response time panelleri ms cinsindendir.
- Request rate paneli sade query kullanir:

```promql
sum(rate(http_server_requests_seconds_count[1m]))
```

## Prometheus Alert Hazirligi

Alert rule taslagi:

```text
infra/observability/prometheus/rules/finance-portal-alerts.yml
```

Hazir alertler:

- `BackendDown`: backend scrape edilemiyor.
- `Http5xxSpike`: HTTP 5xx rate `> 0`.
- `HighApiResponseTime`: average response time `> 2s`.
- `JvmHeapHigh`: heap kullanim orani `> 85%`.
- `HikariPendingConnections`: pending connection `> 0`.

Prometheus config bu rule dosyalarini yukler:

```yaml
rule_files:
  - /etc/prometheus/rules/*.yml
```

## Alerting Faz 1.1

Bu fazda alerting icin Prometheus rule dosyasi ve Grafana alert rule taslaklari hazirlanir. Notification channel/contact point tanimi ortama gore degisecegi icin otomatik bildirim entegrasyonu yapilmaz.

Prometheus rule dosyasi:

```text
infra/observability/prometheus/rules/finance-portal-alerts.yml
```

Grafana alert provisioning taslagi:

```text
infra/observability/grafana/provisioning/alerting/finance-portal-alert-rules.example.yml
```

Grafana taslak dosyasi bilincli olarak `.example.yml` olarak tutulur. Aktif provisioning istenirse dosya ismi `.yml` yapilip Grafana container'ina `/etc/grafana/provisioning/alerting` altindan mount edilmelidir.

### Alert Kurallari

Backend DOWN:

```promql
up{job="finance-portal-backend"} == 0
```

Iliskili panel:

```text
Backend Status
```

HTTP 5xx Error:

```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) > 0
```

Iliskili panel:

```text
5xx Error Rate
```

High Average Response Time:

```promql
(
  sum(rate(http_server_requests_seconds_sum[5m]))
  /
  sum(rate(http_server_requests_seconds_count[5m]))
) > 2
```

Iliskili paneller:

```text
Average Response Time (ms)
Response Time by URI (ms)
```

High JVM Heap Usage:

```promql
sum(jvm_memory_used_bytes{area="heap"})
/
sum(jvm_memory_max_bytes{area="heap"})
> 0.85
```

Iliskili panel:

```text
JVM Heap Used
```

Hikari Pending Connections:

```promql
hikaricp_connections_pending > 0
```

Iliskili panel:

```text
Hikari Pending Connections
```

### Grafana'da Manuel Kurulum

1. Grafana'ya gir: http://localhost:3001
2. Sol menuden `Alerting` > `Alert rules` ekranina git.
3. `New alert rule` sec.
4. Datasource olarak `Prometheus` sec.
5. Yukaridaki PromQL ifadelerinden birini query alanina yaz.
6. Condition bolumunde query sonucunu `IS ABOVE 0` gibi uygun threshold ile degerlendir.
7. `Folder` olarak `Finance Portal` sec.
8. `Evaluation group` icin `Finance Portal Backend` gibi bir grup kullan.
9. `Contact point` ve notification policy'yi ortama gore sec.
10. Rule'u kaydet.

### Test Etme

Prometheus tarafinda query testi:

```text
http://localhost:9090/query
```

veya Prometheus UI icinde `Graph` ekraninda ilgili PromQL'i calistir.

Backend DOWN test:

1. Local backend'i durdur.
2. Prometheus targets ekraninda target'in `DOWN` olmasini bekle.
3. `up{job="finance-portal-backend"} == 0` query'sinin sonuc verdigini dogrula.

HTTP 5xx test:

1. Backend'de 5xx ureten kontrollu bir endpoint varsa onu cagir.
2. Prometheus'ta su query'yi calistir:

```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
```

Response time test:

1. Yavas endpoint veya lokal gecici yuk testi ile request suresini artir.
2. Ortalama sure query'sini calistir.
3. Deger `2` saniyenin ustune cikarsa alert kosulu saglanir.

JVM heap test:

Prometheus'ta orani izle:

```promql
sum(jvm_memory_used_bytes{area="heap"})
/
sum(jvm_memory_max_bytes{area="heap"})
```

Hikari test:

Prometheus'ta pending connection metriğini izle:

```promql
hikaricp_connections_pending
```

Not: Faz 1.1 kapsaminda Kafka, OpenTelemetry veya backend business logic degisikligi yoktur.

## OpenSearch Index Pattern

OpenSearch Dashboards index pattern:

```text
finance-portal-logs-*
```

Index template:

```text
infra/observability/opensearch/finance-portal-logs-template.json
```

Template su alanlari arama/filtreleme icin sabitler:

- `durationMs`: `long`
- `responseTimeMs`: `long`
- `status`: `keyword`
- `statusCode`: `integer`
- `log_type`: `keyword`
- `service`: `keyword`
- `level`: `keyword`
- `uri`: `keyword` + `text`
- `requestId`: `keyword`

Template'i manuel uygulamak icin:

```powershell
Invoke-RestMethod `
  -Method Put `
  -Uri "http://localhost:9200/_index_template/finance-portal-logs-template" `
  -ContentType "application/json" `
  -InFile "infra/observability/opensearch/finance-portal-logs-template.json"
```

Not: Index template yeni olusacak indexler icin gecerlidir. Mevcut index mapping'ini geriye donuk degistirmek reindex gerektirir; Faz 1'de otomatik reindex yapilmaz.

Mevcut gunluk index icinde `durationMs` daha once `text` olarak olustuysa OpenSearch bu alani yerinde `long` tipine ceviremez. Bu durumda iki guvenli secenek vardir:

- Yeni gunluk indexin template ile dogru acilmasini beklemek.
- Kontrollu reindex ile yeni bir indexe gecmek.

Faz 1 stabilizasyonunda veri kaybi riski olmasin diye mevcut index silinmez ve otomatik reindex yapilmaz.

## Fluent Bit JSON Parsing

Tum log dosyalari ayni JSON parser ile okunur:

```text
Parser json
```

Parser tanimi:

```text
infra/observability/fluent-bit/parsers.conf
```

Tail input normal tek satir JSON kayitlari field field parse eder. Ek olarak parser filter vardir:

```text
Key_Name log
Parser json
Preserve_Key Off
```

Bu filter, OpenSearch'e `log: "{\"timestamp\":\"...\"}"` gibi tek string olarak dusme riski olan kayitlarda `log` alaninin icindeki JSON'u da acmayi dener.

## Audit Architecture

Audit logging, business logic'i degistirmeden HTTP/security katmaninda uretilir.

Ana bilesenler:

- `AuditActivityFilter`: Security filter chain icinde calisir, auditlenmesi gereken endpointleri belirler.
- `AuditEvent`: Standart audit event modelidir.
- `AuditEventLogger`: `AUDIT` logger uzerinden structured JSON audit log uretir.
- `audit.log`: Audit eventlerin yazildigi dosyadir.
- Fluent Bit: `audit.log` dosyasini `/var/log/finance-portal/audit.log` pathinden okuyup OpenSearch'e gonderir.

Audit event alanlari:

```text
timestamp, userId, username, role, action, resourceType, resourceId,
endpoint, method, ipAddress, userAgent, success, durationMs, requestId, log_type
```

`log_type` degeri:

```text
audit
```

Auditlenen eventler:

- `LOGIN_SUCCESS`: Backend login endpointi kullanilirsa basarili login.
- `LOGIN_FAILURE`: Backend login endpointi kullanilirsa basarisiz login.
- `LOGOUT`: Backend logout endpointi kullanilirsa logout.
- `PORTFOLIO_CREATE`: `POST /api/v1/portfolios/{userId}`
- `PORTFOLIO_UPDATE`: `PUT /api/v1/portfolios/{portfolioId}`
- `PORTFOLIO_DELETE`: `DELETE /api/v1/portfolios/{portfolioId}`
- `ALARM_CREATE`: `POST /api/v1/alerts/{userId}`
- `ALARM_DELETE`: `PATCH /api/v1/alerts/{userId}/{alertId}/cancel`
- `ADMIN_ACTION`: `/api/v1/admin/**`
- `USER_ROLE_CHANGE`: `PATCH /api/v1/admin/users/{userId}/role`
- `CRITICAL_ENDPOINT_ACCESS`: premium AI, portfolio holding ve watchlist gibi kritik endpoint erisimleri.

Not: Development ortaminda login genellikle Keycloak tarafinda gerceklesir. Backend login/logout endpointi yoksa `LOGIN_SUCCESS`, `LOGIN_FAILURE` ve `LOGOUT` eventleri backend audit logunda uretilmez; model ve pattern destegi hazirdir.

Ornek audit JSON:

```json
{
  "timestamp": "2026-05-16T00:10:12.123Z",
  "level": "INFO",
  "logger": "AUDIT",
  "thread": "http-nio-8080-exec-4",
  "service": "finance-portal-backend",
  "message": "Audit event recorded",
  "log_type": "audit",
  "requestId": "7fd2d75f-0a64-4b8a-a44a-2db31250f9f2",
  "userId": "42",
  "username": "admin@example.com",
  "role": "ROLE_ADMIN",
  "action": "USER_ROLE_CHANGE",
  "resourceType": "USER",
  "resourceId": "17",
  "endpoint": "/api/v1/admin/users/17/role",
  "method": "PATCH",
  "ipAddress": "127.0.0.1",
  "userAgent": "Mozilla/5.0",
  "success": "true",
  "durationMs": "38"
}
```

Audit test senaryolari:

1. Backend'i calistir.
2. Yetkili token ile `POST /api/v1/portfolios/{userId}` cagir.
3. `backend/logs/audit.log` icinde `PORTFOLIO_CREATE` eventini kontrol et.
4. OpenSearch Discover'da `log_type:"audit"` sorgusunu calistir.
5. Admin token ile `PATCH /api/v1/admin/users/{userId}/role` cagir ve `USER_ROLE_CHANGE` eventini kontrol et.

Audit OpenSearch sorgulari:

```text
log_type:"audit"
```

```text
action:"LOGIN_SUCCESS"
```

```text
resourceType:"PORTFOLIO"
```

```text
log_type:"audit" AND action:"PORTFOLIO_CREATE"
```

```text
log_type:"audit" AND action:"USER_ROLE_CHANGE"
```

```text
log_type:"audit" AND success:"false"
```

## Discover Sorgulari

Access logs:

```text
log_type:"access"
```

Errors:

```text
level:"ERROR"
```

Scheduler logs:

```text
log_type:"scheduler"
```

Audit logs:

```text
log_type:"audit"
```

Request ID arama:

```text
requestId:"<request-id>"
```

Yavas requestler:

```text
log_type:"access" AND durationMs:>1000
```

5xx response'lar:

```text
log_type:"access" AND status:/5../
```

Endpoint arama:

```text
log_type:"access" AND uri:"/api/v1/markets"
```

Provider hatalari:

```text
success:false AND provider:*
```

## Saved Search Onerileri

OpenSearch Dashboards uzerinde otomatik saved search olusturmak Faz 1'de zorunlu degildir. Manuel olusturma:

1. OpenSearch Dashboards > Discover ekranina git.
2. Index pattern olarak `finance-portal-logs-*` sec.
3. Sorguyu gir.
4. Kolonlari sec.
5. `Save` ile asagidaki isimlerden biriyle kaydet.

Onerilen saved search'ler:

- `Access Logs`: `log_type:"access"`
- `Errors`: `level:"ERROR"`
- `Scheduler Logs`: `log_type:"scheduler"`
- `Audit Logs`: `log_type:"audit"`

Onerilen kolonlar:

```text
timestamp, level, log_type, service, requestId, method, uri, status, durationMs, logger, message
```

## Retention / Lifecycle Plani

Faz 1'de otomatik silme aktif edilmez; yanlis lifecycle ayari veri kaybina yol acabilecegi icin sadece plan dokumante edilir.

Oneri:

- `access`, `application`, `scheduler`: 14-30 gun
- `error`, `audit`: 90 gun

Uygulama secenekleri:

- OpenSearch Index State Management policy
- Gunluk index pattern uzerinden periyodik cleanup job
- Snapshot alinmadan audit/error indexlerini silmeme

## Fluent Bit Troubleshooting

Config:

```text
infra/observability/fluent-bit/fluent-bit.conf
```

Izlenen dosyalar:

```text
/var/log/finance-portal/application.log
/var/log/finance-portal/access.log
/var/log/finance-portal/error.log
/var/log/finance-portal/audit.log
/var/log/finance-portal/scheduler.log
```

Kontroller:

```powershell
docker logs finance-portal-fluent-bit --tail 100
docker exec finance-portal-fluent-bit ls -la /var/log/finance-portal
docker run --rm -v infra_fluent_bit_state:/state busybox ls -la /state
```

Yeni log gelmiyorsa:

- Backend'in ilgili `.log` dosyasina satir yazdigini dogrula.
- Fluent Bit mount path'i ile backend log path'i ayni mi kontrol et.
- Tail DB offset dosyalari eski pozisyonda kalmis olabilir.
- JSON satirlari tek satir ve UTF-8 olmali.
- OpenSearch output loglarinda `_bulk` status `200` ve `errors:false` beklenir.

Access offset reset ornegi:

```powershell
docker run --rm -v infra_fluent_bit_state:/state busybox sh -c "rm -f /state/fluent-bit-finance-portal-access.db*"
docker compose -f infra/docker-compose.yml restart fluent-bit
```

## Prometheus Target DOWN Troubleshooting

Target durumunu kontrol et:

```text
http://localhost:9090/targets
```

Backend local host uzerinde calisiyorsa container icinden test:

```powershell
docker exec finance-portal-prometheus wget -qO- http://host.docker.internal:8080/actuator/prometheus
```

Backend Docker icinde calisiyorsa:

```powershell
docker exec finance-portal-prometheus wget -qO- http://backend:8080/actuator/prometheus
```

Yaygin nedenler:

- Backend calismiyor.
- Yanlis Prometheus target kullaniliyor.
- `/actuator/prometheus` endpoint'i kapali.
- Docker network veya port mapping hatali.
- Local firewall host erisimini engelliyor.

## OpenTelemetry Tracing

Bağımlılıklar (`pom.xml`):

- `micrometer-tracing-bridge-otel`: OTel bridge, traceId/spanId'yi otomatik MDC'ye yazar.
- `opentelemetry-exporter-otlp`: Trace'leri OTLP collector'a gönderir.
- `opentelemetry-log4j-appender-2.17`: Log4j2 OTel bridge.

Yapılandırma (`application.yml`):

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # dev: tüm requestler; prod: 0.1 önerilir
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:}  # boş = OTLP export devre dışı
```

Collector dağıtımı olmadan traceId/spanId MDC'de üretilir ve her JSON log satırında bulunur. Trace export için `OTEL_EXPORTER_OTLP_ENDPOINT` env değişkeni ile Jaeger/Tempo gibi bir collector hedeflenir.

## Kafka Log Pipeline Kararı

İsterde log4j → Kafka → consumer → OpenSearch akışı talep edilmiştir. Aşağıdaki karar değerlendirmesi uygulanmıştır:

| Seçenek | Mevcut Durumda Uygulanabilirlik |
|---------|-------------------------------|
| Fluent Bit → OpenSearch | ✅ Aktif, çalışıyor, tam entegre |
| Kafka → Consumer → OpenSearch | Proje infra'sında Kafka yok; Kafka + consumer + zookeeper eklenmesi +3 servis demektir |

**Alınan karar:** Fluent Bit pipeline aktif olarak kullanılmaktadır. Kafka isteri için iki yaklaşım sunulmaktadır:

### A) Log4j2 Kafka Appender (Demo/Ek servis)

`pom.xml`'e `log4j-kafka` appender eklenir, `log4j2-spring.xml`'e Kafka appender tanımlanır, docker-compose'a Kafka + Zookeeper eklenir. Log consumer olarak Kafka Connect sink veya basit Spring Boot consumer yazılır.

Bu yaklaşım için gerekli ek:
```xml
<!-- pom.xml'e eklenecek -->
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
</dependency>
```

```xml
<!-- log4j2-spring.xml'e eklenecek appender -->
<Kafka name="KafkaAppender" topic="finance-portal-logs">
    <JsonTemplateLayout eventTemplateUri="classpath:log4j2-json-event-template.json"/>
    <Property name="bootstrap.servers">${env:KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}</Property>
</Kafka>
```

### B) Mevcut Fluent Bit (Aktif)

Mevcut akış: `log dosyaları → Fluent Bit → OpenSearch`

Bu akış, Kafka'nın sağladığı dayanıklı kuyruk garantilerini sağlamaz; ancak küçük-orta ölçekli bir proje için yeterlidir. Dosya tabanlı buffering (`fluent-bit/state/*.db`) veri kaybını minimize eder.

## Güvenlik Olayları Log Mimarisi

| Olay | Log Tipi | Logger | Nerede |
|------|----------|--------|--------|
| 401 Unauthorized | security_event | HttpRequestLoggingFilter | access.log + console |
| 403 Forbidden | security_event | AiPremiumAccessDeniedHandler | application.log + console |
| Admin işlemleri | audit | AUDIT | audit.log |
| Rol değişikliği | audit | AUDIT | audit.log |
| Portfolio CRUD | audit | AUDIT | audit.log |

OpenSearch sorgusu:
```text
log_type: "security_event"
```

## Observability Checklist

### Backend Loglama
- [x] Log4j2 JSON format (48+ alan)
- [x] requestId/traceId/spanId/userId MDC'de
- [x] Request/response access log
- [x] Exception structured log
- [x] Audit log (admin/portfolio/alert)
- [x] Scheduler log (log_type=scheduler)
- [x] External provider log
- [x] Sensitive data masking

### Metrics & Tracing
- [x] Spring Boot Actuator exposed
- [x] Micrometer Prometheus endpoint
- [x] OTel tracing sampling %100 (dev)
- [x] traceId/spanId JSON loglarda
- [x] JVM heap/CPU metrics
- [x] HikariCP DB pool metrics
- [x] HTTP server request metrics

### Log Pipeline
- [x] Fluent Bit → OpenSearch akışı
- [x] Index template (finance-portal-logs-*)
- [x] Auto-apply (opensearch-init container)
- [x] JSON parsing doğru

### Prometheus + Grafana
- [x] Prometheus docker target (`backend:8080`)
- [x] 5 alert rule tanımlı
- [x] Grafana provisioned datasource
- [x] Grafana provisioned dashboard (65KB)

### OpenSearch
- [x] Index pattern: finance-portal-logs-*
- [x] 20+ örnek sorgu
- [x] Security event sorguları
- [x] Trace ID ile istek takibi

### Güvenlik
- [x] 401/403 log_type=security_event
- [x] Admin op audit log
- [x] Token/password/header maskeleme
