# Finance Portal – Keycloak Theme Setup

## Dizin yapısı

```
infra/backend/docker/keycloak/themes/
└── finance-portal/
    └── login/
        ├── theme.properties          ← parent=keycloak, CSS listesi
        └── resources/
            ├── css/
            │   └── finance-portal.css   ← tüm görsel override'lar
            └── img/
                └── logo.svg             ← trend çizgisi SVG logosu
```

## Docker Compose – Volume mount

`infra/docker-compose.yml` içinde Keycloak servisine şu volume **zaten eklenmiş durumda**:

```yaml
keycloak:
  volumes:
    - ./backend/docker/keycloak/themes:/opt/keycloak/themes
```

Bu mount, `themes/` klasörünü Keycloak'ın `/opt/keycloak/themes/` dizinine bağlar.
`start-dev` modunda Keycloak tema dosyalarını doğrudan diskten okur; theme cache'i yoktur.

## Realm ayarı – loginTheme

`finance-portal-realm.json` dosyasına `"loginTheme": "finance-portal"` **zaten eklenmiş**.
Bu alan yeni realm import işlemlerinde otomatik olarak temayı aktif eder.

**Mevcut çalışan bir Keycloak için** (realm zaten import edilmişse):
1. Keycloak Admin Console → http://localhost:8081
2. Realm: `finance-portal` → Realm Settings → Themes sekmesi
3. Login Theme = `finance-portal` seç → Save

## Keycloak'ı yeniden başlatma

```bash
docker compose -f infra/docker-compose.yml restart keycloak
```

Yeniden başlatma sonrası `http://localhost:8081/realms/finance-portal/account` adresini
açarak temayı önizleyebilirsin. Login sayfası Finance Portal tasarımıyla görünmeli.

## Tema nasıl çalışır

- **parent=keycloak**: PatternFly 3 layout, FreeMarker template'leri ve form yapısını devralır.
  Authentication flow (OTP, forgot-password, email-verify, register) değişmez.
- **finance-portal.css**: PatternFly'ın üzerine görsel override uygular.
  Arka plan gradient, card, input, button, alert renkleri frontend ile eşleştirilmiştir.
- **logo.svg**: CSS `::before` pseudo-element ile header alanına enjekte edilir.
  Template değişikliği gerektirmez.

## Yeni sayfa tipi eklemek

Keycloak, login flow için tüm sayfaları (login.ftl, register.ftl, login-otp.ftl,
login-reset-password.ftl, login-verify-email.ftl vb.) aynı base template'den üretir.
Dolayısıyla `finance-portal.css` tüm bu sayfalara otomatik olarak uygulanır.

Herhangi bir sayfanın HTML yapısını özelleştirmek istersen, ilgili `.ftl` dosyasını
`login/` klasörüne kopyalayıp düzenleyebilirsin. Keycloak önce kendi tema dizinine bakar,
bulamazsa parent'tan okur.

## Önemli notlar

- `DELETE /api/v1/admin/ai/cache/**` endpoint'i şu an `permitAll` olarak açık;
  production'a almadan önce güvenli hale getir (ADMIN rolü ekle veya kaldır).
- Bu theme yalnızca görsel katmana dokunur. JWT token, Keycloak flow, Spring Security
  ve frontend token handling tamamen değişmeden çalışmaya devam eder.
