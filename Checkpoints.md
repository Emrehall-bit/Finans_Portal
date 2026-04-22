Flyway kurulumu tamamlandı:
Yeni bir tablo eklemem veya mevcut tabloya yeni bir kolon eklemem gerektiğinde,
kolon adı değişikliği, vb durumlarda flywayde müdahale edilir.

// API GATEWAY?

//npm install npm run dev/npm start (modülleri kur ve fr çalıştır)
//./mvnw spring-boot:run (backend çalıştır)

//KeyCloak
SecurityConfig’i kapattım: sadece POST /api/v1/users ve GET /actuator/health
public kaldı, diğer her şey artık login gerektiriyor.
Ayrıca sadece giriş yapmakla yetinmeyip sahiplik kontrolü de ekledim. 
Yani kullanıcı kendi userId’si dışındaki alerts, portfolios, watchlist 
gibi endpoint’lere erişemiyor; portfolioId ve watchlist id üzerinden de 
başka kullanıcı verisine gitmesi engelleniyor. Bunu JWT içindeki Keycloak
sub değeri ile veritabanındaki keycloakId eşleştirerek yaptım.

Docker Compose Revizyon Notları - Altyapı İzolasyonu
Tarih: 20 Nisan 2026

Projenin aktif geliştirme sürecinde yaşanan port çakışmalarını (Port 8080 in use) ve ağ izolasyonu sorunlarını (Connection to localhost:5433 refused) çözmek amacıyla docker-compose.yml dosyası bir "Tam Paket" (Full-Stack) formatından çıkarılarak sadece "Altyapı" (Infrastructure) formatına dönüştürülmüştür.

❌ Silinen Kod Bloğu 1: frontend Servisi
YAML
frontend:
build:
context: ../frontend
dockerfile: Dockerfile
container_name: finance_portal_frontend
ports:
- "3000:80"
depends_on:
backend:
condition: service_started
keycloak:
condition: service_started
restart: unless-stopped
Silinme Nedeni: Frontend geliştirmesi aktif olarak npm run start (veya dev) ile React/Node ortamında yapılmaktadır. Bu bloğun Docker içinde kalması, her kod değişikliğinde Docker image'ının yeniden build edilmesini gerektirecek ve geliştirme hızını düşürecekti. Uygulama kodu host makineye taşındı.

❌ Silinen Kod Bloğu 2: backend Servisi
YAML
backend:
build:
context: ../backend
dockerfile: Dockerfile
container_name: finance_portal_backend
ports:
- "8080:8080"
environment:
SPRING_PROFILES_ACTIVE: prod
# ... diğer environment ayarları ...
depends_on:
postgres:
condition: service_healthy
redis:
condition: service_healthy
keycloak:
condition: service_started
restart: unless-stopped
Silinme Nedeni: Yaşanan tüm çakışmaların ana kaynağı bu bloktu.

Port Çakışması: Bu servis Docker ayağa kalktığında 8080 portunu işgal ediyor, bu nedenle IDE (IntelliJ) üzerinden Spring Boot çalıştırılmak istendiğinde port çakışması hatası alınıyordu.

Ağ İzolasyonu: Docker, servisleri kendi iç ağında (network) tutar. IDE üzerinden 8080 portundaki Docker backend'i zorla kapatılıp IDE backend'i ayağa kaldırıldığında, yerel uygulama Docker'ın izole ağına giremediği için 5433 portundaki PostgreSQL'e ulaşamıyor ve "Connection Refused" hatası fırlatıyordu.

Sonuç: Uygulama katmanları (Frontend & Backend) IDE ve terminal üzerinden host bilgisayarda çalıştırılmak üzere Compose dosyasından çıkarıldı. Docker sadece veritabanı (PostgreSQL), önbellek (Redis) ve kimlik yönetimi (Keycloak) servislerini barındıran temiz bir altyapı sağlayıcısı haline getirildi.














📝 Checkpoint: Kimlik Doğrulama ve Keycloak Mimarisi
Durum: Altyapı (Docker) ve Backend (Spring Boot) entegrasyonu tamamlandı. Port çakışmaları çözüldü.

1. Keycloak'un Görevi (Otel Resepsiyonu Mantığı)
   Sistemde Keycloak, merkezi kimlik sunucusudur (Identity Provider). Kullanıcı yönetimi, kayıt olma ve giriş yapma süreçlerini backend'den bağımsız olarak yönetir.

Neden Ayrı? Backend'in her seferinde "şifre doğru mu?" kontrolü yapması yerine, Keycloak bu yükü üstlenir ve güvenli bir "anahtar" (Token) üretir.

Port: Docker üzerinde 8081 portunda çalışır.

2. JWT Token Nedir ve Nasıl Çalışır?
   Kullanıcı giriş yaptığında Keycloak ona bir JWT (JSON Web Token) verir. Bu token şunları içerir:

Header: Algoritma bilgisi.

Payload: Kullanıcı ID'si, e-postası ve yetkileri (Roles: ADMIN, USER).

Signature: Token'ın değiştirilmediğini kanıtlayan dijital imza.

3. Otomatik vs. Manuel Token Akışı
   Gerçek Senaryo (Otomatik): 1. Kullanıcı Frontend'e gider.
2. Frontend kullanıcıyı Keycloak Login sayfasına yönlendirir.
3. Giriş başarılı olunca Keycloak token'ı Frontend'e gönderir.
4. Frontend bu token'ı hafızaya alır ve her Backend isteğinde başlığa (Authorization: Bearer <token>) ekler.

Geliştirme/Test Senaryosu (Manuel): Frontend henüz hazır değilse veya sadece API test edilecekse, geliştirici Postman gibi bir araçla Keycloak'tan token'ı "elle" talep eder ve Backend'e gönderir.

4. Neden 401 Unauthorized Hatası Alıyorum?
   Tarayıcıdan doğrudan localhost:8080 adresine gidildiğinde alınan 401 hatası, sistemin başarıyla korunduğunu gösterir. Spring Security, gelen isteğin başlığında geçerli bir JWT Token bulamadığı için "içeri giremezsin" diyerek kapıyı kapatmaktadır.

5. Sıradaki Adım: Token ile API Erişimi
   Backend'deki korumalı endpoint'lere (örneğin: /api/portfolio) erişmek için:

Keycloak'tan bir access_token alınmalı.

Bu token, HTTP isteğinin Authorization header'ına eklenmeli.
(Bundan sonraki adım frontend'i bağlamak — React uygulaması Keycloak'a login olsun, token'ı otomatik backend'e göndersin. Prompt 3'ü Codex'e verdin mi, frontend tarafı hazır mı?)

