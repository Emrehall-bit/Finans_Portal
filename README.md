# Finance Portal

Gerçek zamanlı piyasa verileri, portföy yönetimi, teknik/temel analiz ve yapay zeka destekli yorumlar içeren full-stack bir finans uygulamasıdır.

---

## Ön Koşullar

| Araç | Sürüm |
| --- | --- |
| Docker Desktop | 24+ |
| Docker Compose | v2 (`docker compose` komutu) |

Java veya Node.js kurulumuna gerek yoktur; tüm bileşenler container ortamında derlenmektedir.

---

## Kurulum ve Çalıştırma

### 1. Depoyu Klonlayın

```bash
git clone https://github.com/Emrehall-bit/Finans_Portal
cd Finans_Portal
```

### 2. API Anahtarlarını Girin

`infra/.env` dosyası repoda şablon olarak mevcuttur. api-keyler githuba gönderilemediğinden, Toyota Projeleri üzerinden iletilen kurulum notundaki değerleri bu dosyaya yapıştırabilirsiniz.

### 3. Servisleri Başlatın

```bash
cd infra
docker compose up -d --build
```

> **İlk derlemede** Maven ve npm build süreçleri çalışacağından yaklaşık **10 dakika** beklenmesi gerekmektedir. Sonraki başlatmalarda `--build` olmadan çok daha hızlı ayağa kalkar.

### 4. Servislerin Hazır Olduğunu Doğrulayın

```bash
docker compose ps
```

Aşağıdaki servisler `Up` veya `Up (healthy)` durumuna geldiğinde uygulama kullanıma hazırdır:

| Servis | Beklenen Durum |
| --- | --- |
| frontend | Up |
| backend | Up (healthy) |
| postgres | Up (healthy) |
| keycloak | Up (healthy) |
| redis | Up |
| kafka | Up |

### 5. Uygulamaya Erişin

**http://localhost:3000**

---

## Test Hesapları
Aşağıdaki test hesapları ile giriş yapabilir veya kendiniz hesap oluşturabilirsiniz:

| Kullanıcı Adı | Şifre | Rol | 2FA |
| --- | --- | --- | --- |
| `adminuser` | `admin123` | ADMIN | Yok — doğrudan giriş |
| `testuser` | `test123` | USER | İlk girişte kurulur |
| `premiumuser` | `premium123` | USER_PREMIUM | İlk girişte kurulur |

### 2FA Kurulumu (`testuser` / `premiumuser` için)

`testuser` veya `premiumuser` ile ilk girişte Keycloak otomatik olarak 2FA kurulum ekranı açar:

1. Telefonunuza **Google Authenticator** veya **Authy** uygulamasını indirin.
2. Uygulamada **"QR Kodu Tara"** (veya **+**) seçeneğine dokunun.
3. Ekrandaki QR kodu tarayın.
4. Uygulama 6 haneli bir kod üretecektir; bu kodu **"One-time code"** alanına girin ve **"Submit"** butonuna basın.
5. Kurulum tamamlanır; sonraki her girişte bu kod istenecektir.

> `adminuser` hesabında 2FA bilerek devre dışı bırakılmıştır — hızlı test erişimi için doğrudan giriş yapılabilir.

---

## Test Senaryoları

**`adminuser` ile:**
- `http://localhost:3000/admin` — kullanıcı yönetimi, veri yönetimi, denetim kayıtları

**`testuser` ile:**
- Portföy oluşturma (hisse, kripto, fon, döviz pozisyonları)
- Watchlist ve fiyat alarmı tanımlama
- Teknik analiz (TradingView grafikleri, SMA/EMA/RSI/MACD)
- Haber akışı (AA, CNBC, KAP)

**`premiumuser` ile:**
- AI destekli teknik/temel analiz yorumu
- Portföy AI analizi ve benchmark karşılaştırması
- Dashboard piyasa özeti

---

## API Dokümantasyonu

| Kaynak | Adres |
| --- | --- |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

Swagger UI'ya erişmek için oturum açılmasına gerek yoktur.

---

## Servis Adresleri

| Servis | Adres | Kimlik |
| --- | --- | --- |
| Uygulama | http://localhost:3000 | — |
| Backend API | http://localhost:8080 | — |
| Keycloak | http://localhost:8081 | admin / admin |
| Grafana | http://localhost:3001 | admin / admin |
| Kafka UI | http://localhost:8082 | — |
| Prometheus | http://localhost:9090 | — |
| OpenSearch Dashboards | http://localhost:5601 | — |





## Teknik Yığın

- **Backend:** Java 21, Spring Boot 3.5, PostgreSQL 16, Redis 7, Keycloak 26.1, Kafka, Resilience4j, jBPM
- **Frontend:** React 19, Vite, TanStack Query 5, Keycloak JS, i18next, Recharts, TradingView Lightweight Charts
- **Altyapı:** Docker Compose (14 servis), Nginx, Prometheus, Grafana, Grafana Tempo, OpenSearch, OpenLDAP

---

## Mimari

```
                    ┌─────────────┐
                    │   Browser   │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
       ┌──────▼──────┐ ┌──▼───┐ ┌──────▼──────┐
       │   Frontend   │ │ KC   │ │   Grafana   │
       │  :3000 nginx │ │:8081 │ │    :3001    │
       └──────┬───────┘ └──────┘ └─────────────┘
              │
       ┌──────▼──────┐
       │   Backend   │──── Redis (:6379)
       │    :8080    │──── PostgreSQL (:5433)
       └──────┬──────┘──── Keycloak (JWT)
              │
     ┌────────┼────────┐
     │        │        │
  ┌──▼──┐ ┌──▼───┐ ┌──▼────┐
  │Kafka│ │Tempo │ │Prom.  │
  │:9092│ │:4318 │ │:9090  │
  └──┬──┘ └──────┘ └───────┘
     │
  ┌──▼──────────┐
  │ OpenSearch  │
  │   :9200     │
  └─────────────┘
```
