# OpenSearch Dashboards Operasyon Rehberi

Bu klasor, `finance-portal-logs-*` index pattern'i uzerinde incident debugging icin kullanilacak operasyonel gorunumleri ve kaydedilebilir sorgulari icerir.

## Hazirlanacak Gorunumler

### 1. ERROR Logs

Amac:
- Exception ve kritik hata takibi

Filtre:
- `level: "ERROR"`

Kolonlar:
- `timestamp`
- `level`
- `logger`
- `message`
- `exception`
- `requestId`
- `uri`
- `status`

### 2. WARN + ERROR

Amac:
- Anormal durumlari erken fark etmek

Filtre:
- `level: ("WARN" OR "ERROR")`

Kolonlar:
- `timestamp`
- `level`
- `logger`
- `message`
- `requestId`
- `uri`
- `status`

### 3. HTTP 404 / 5xx Tracking

Amac:
- Hatali endpoint ve backend exception analizi

Filtre alternatifleri:
- `status: "404"`
- `status: /5../`

Kolonlar:
- `timestamp`
- `status`
- `method`
- `uri`
- `message`
- `requestId`
- `exception`

### 4. Slow Requests

Amac:
- Yavas endpointleri gormek

Filtre:
- `durationMs > 500`

Not:
- Eger `durationMs` field'i numeric map edilmediyse once Discover ekraninda field tipini kontrol et.
- Numeric degilse ingestion veya mapping iyilestirmesi gerekebilir.

Kolonlar:
- `timestamp`
- `uri`
- `method`
- `durationMs`
- `requestId`
- `status`
- `message`

### 5. Provider Failures

Amac:
- `BINANCE`, `EVDS`, `TEFAS`, `YAHOO` gibi provider tarafli problemleri izlemek

Filtre alternatifleri:
- `message: "*Provider*"`
- `message: "*refresh failed*"`
- `message: "*fetch failed*"`
- `message: "*returned no market data*"`
- `message: "*Unauthorized*"`

Kolonlar:
- `timestamp`
- `level`
- `logger`
- `message`
- `source`
- `providerName`
- `requestId`
- `exception`

### 6. requestId Search Flow

Amac:
- Tek bir request'in access log, business log, provider log ve exception log akisini birlikte izlemek

Filtre:
- `requestId: "<REQUEST_ID>"`

Kolonlar:
- `timestamp`
- `level`
- `logger`
- `message`
- `requestId`
- `method`
- `uri`
- `status`
- `durationMs`
- `exception`

## Onerilen Dashboard Organizasyonu

### Incident Response

Paneller:
- ERROR Logs
- WARN + ERROR
- HTTP 404 / 5xx Tracking
- Slow Requests

### Provider Health

Paneller:
- Provider Failures
- `message: "*Unauthorized*"`
- `message: "*rate limit*"`

### Request Trace

Paneller:
- requestId Search Flow

## Kullanım

1. OpenSearch Dashboards ac:
   - `http://localhost:5601`
2. Data view olarak `finance-portal-logs-*` sec
3. Discover ekraninda [queries.md](./queries.md) icindeki sorgulari uygula
4. Ihtiyac olan gorunumleri `Save search` ile kaydet
5. Bu saved search'leri yeni dashboard'lara ekle

## Pratik Notlar

- Sunum icin ilk acilacak ekran `ERROR Logs` veya `WARN + ERROR` olsun.
- Incident aninda once `timestamp desc` siralama kullan.
- Tek request takibinde `requestId` en guclu alan.
- Provider sorunlarinda `logger`, `source`, `providerName` ve `message` birlikte okunmali.
