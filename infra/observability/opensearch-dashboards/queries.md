# OpenSearch Dashboards Query Katalogu

Bu dosya Discover veya Dashboard filtrelerinde kullanmak icin hazir sorgulari icerir.

## ERROR Logs

```text
level: "ERROR"
```

## WARN + ERROR

```text
level: ("WARN" OR "ERROR")
```

## HTTP 404

```text
status: "404"
```

## HTTP 5xx

```text
status: /5../
```

## HTTP 404 or 5xx

```text
status: "404" OR status: /5../
```

## Slow Requests

```text
durationMs > 500
```

## Provider Failures - Generic

```text
message: "*Provider*" OR message: "*refresh failed*" OR message: "*fetch failed*" OR message: "*returned no market data*"
```

## Provider Unauthorized

```text
message: "*Unauthorized*"
```

## Provider Rate Limit

```text
message: "*rate limit*" OR message: "*Rate limit*"
```

## Market Provider Focus

```text
logger: "com.emrehalli.financeportal.market*"
```

## News Provider Focus

```text
logger: "com.emrehalli.financeportal.news*"
```

## Request Trace by requestId

```text
requestId: "<REQUEST_ID>"
```

## Access Logs Only

```text
message: "HTTP request completed"
```

## Exceptions with requestId

```text
level: "ERROR" AND requestId: *
```

## 404 Requests with URI Context

```text
status: "404" AND uri: *
```

## Long Running Failed Requests

```text
durationMs > 500 AND status: /5../
```

## Onerilen Discover Kolonlari

### ERROR Logs

```text
timestamp, level, logger, message, exception, requestId, uri, status
```

### Slow Requests

```text
timestamp, uri, method, durationMs, requestId, status, message
```

### Provider Failures

```text
timestamp, level, logger, source, providerName, message, requestId, exception
```

### Request Trace

```text
timestamp, level, logger, message, requestId, method, uri, status, durationMs, exception
```
