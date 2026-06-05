# OpenLDAP — Keycloak User Federation

## Mimarideki Rolü

```
Frontend → Keycloak → OpenLDAP (yazılabilir directory server)
Backend  → Keycloak JWT doğrulama (LDAP ile konuşmaz)
```

Tüm kullanıcı lifecycle'ı OpenLDAP üzerinde yaşar:

| Olay | Davranış |
|------|----------|
| Kayıt (registration) | Keycloak kullanıcıyı LDAP'a yazar (`syncRegistrations: true`) |
| Login | Keycloak LDAP'a bind eder, parolayı LDAP'ta doğrular |
| Parola değişikliği | Keycloak yeni parolayı LDAP'a yazar (`editMode: WRITABLE`) |
| Profil güncelleme | Keycloak LDAP'taki `mail`, `givenName`, `sn` değerlerini günceller |
| Backend JWT | Sadece JWT doğrular; LDAP ile konuşmaz |

---

## LDAP Yapısı

```
dc=financeportal,dc=local
├── cn=admin      — image tarafından oluşturulur (Keycloak bind hesabı)
├── cn=readonly   — image tarafından oluşturulur (kullanılmıyor, mevcut)
├── ou=users      — 01-structure.ldif
│   └── uid=ldaptest  — seed kullanıcı (01-structure.ldif)
└── ou=groups     — 01-structure.ldif
```

---

## Keycloak Federation Ayarları

| Parametre | Değer | Açıklama |
|-----------|-------|---------|
| editMode | `WRITABLE` | Keycloak LDAP'a yazabilir |
| syncRegistrations | `true` | Kayıt → LDAP'a yansır |
| importEnabled | `true` | LDAP kullanıcıları Keycloak local DB'ye aktarılır |
| bindDn | `cn=admin,dc=financeportal,dc=local` | Yazma yetkili |
| bindCredential | `ldapadmin123` | `.env` LDAP_ADMIN_PASSWORD ile eşleşmeli |
| usersDn | `ou=users,dc=financeportal,dc=local` | |
| userObjectClasses | `inetOrgPerson` | |
| username attr | `uid` | |
| rdnAttr | `uid` | DN: `uid=<username>,ou=users,...` |

**Mapper özeti:**

| LDAP attr | Keycloak attr | JWT claim | read.only |
|-----------|---------------|-----------|-----------|
| `uid` | username | preferred_username | `true` (immutable) |
| `mail` | email | email | `false` |
| `givenName` | firstName | given_name | `false` |
| `sn` | lastName | family_name | `false` |
| `cn` | — | — | `false`, write-only (full-name-ldap-mapper) |

`full-name-ldap-mapper`: `firstName + " " + lastName` → `cn` yazar; `inetOrgPerson` için zorunlu `cn` attribute'unu otomatik doldurur.

---

## bindCredential Uyarısı

Realm import `bindCredential` alanında env var desteklemez. Şifre realm JSON'a sabit yazılıdır:

```
finance-portal-realm.json → "bindCredential": ["ldapadmin123"]
infra/.env                → LDAP_ADMIN_PASSWORD=ldapadmin123
```

Şifreyi değiştirirsen:
- **Seçenek A (dev):** `realm JSON`'u güncelle + volume sıfırla
- **Seçenek B:** Keycloak Admin UI → User Federation → openldap → Credentials sekmesinden güncelle (DB'ye yazılır)

---

## Production Notu: Service Account

`cn=admin` tüm dizine tam erişime sahiptir. Production ortamı için:

```ldif
# Dedicated service account (cn=config ACL ile kısıtlanmış)
dn: uid=keycloak-svc,ou=users,dc=financeportal,dc=local
objectClass: inetOrgPerson
uid: keycloak-svc
cn: Keycloak Service
sn: Service
userPassword: {güçlü şifre}
```

OpenLDAP ACL (cn=config üzerinden) ile bu hesaba yalnızca `ou=users` altında yazma yetkisi verilmeli; `cn=admin` yerine kullanılmalı.

---

## Adım Adım Test Akışı

### 1. Başlat

```bash
cp infra/.env.example infra/.env
cd infra && docker compose up -d

# OpenLDAP healthy olana kadar bekle (~30 sn)
docker compose ps openldap   # "(healthy)" görünmeli
```

### 2. Seed kullanıcıyı doğrula

```bash
docker exec finance_portal_openldap ldapsearch \
  -x -H ldap://localhost \
  -D "cn=admin,dc=financeportal,dc=local" \
  -w "${LDAP_ADMIN_PASSWORD:-ldapadmin123}" \
  -b "ou=users,dc=financeportal,dc=local" \
  "(uid=ldaptest)"
# uid: ldaptest ve givenName: LDAP görünmeli
```

### 3. Keycloak federation doğrula

1. http://localhost:8081 → admin / admin
2. `finance-portal` → **User Federation** → `openldap` görünmeli
3. **Action → Test connection** → "LDAP Connection successful"
4. **Action → Test authentication** → admin bind doğrulanır

### 4. Uygulama login testi (seed kullanıcı)

http://localhost:3000 → Giriş Yap:
```
Kullanıcı adı: ldaptest
Parola:        ldaptest123
```

### 5. Kayıt testi (LDAP'a yeni kullanıcı)

http://localhost:3000 → Kayıt Ol (First Name, Last Name, Email, Username, Password doldur).

Kayıt tamamlandıktan sonra LDAP'ta kullanıcıyı doğrula:

```bash
docker exec finance_portal_openldap ldapsearch \
  -x -H ldap://localhost \
  -D "cn=admin,dc=financeportal,dc=local" \
  -w "${LDAP_ADMIN_PASSWORD:-ldapadmin123}" \
  -b "ou=users,dc=financeportal,dc=local" \
  "(uid=<yeni-kullanici-adi>)"
# Yeni kullanıcı LDAP'ta görünmeli
```

### 6. Token doğrulaması

```bash
curl -s -X POST \
  "http://localhost:8081/realms/finance-portal/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=finance-portal-backend&grant_type=password&username=ldaptest&password=ldaptest123" \
  | jq '{sub: .access_token | split(".")[1] | @base64d | fromjson | .sub,
         preferred_username: .access_token | split(".")[1] | @base64d | fromjson | .preferred_username,
         email: .access_token | split(".")[1] | @base64d | fromjson | .email,
         roles: .access_token | split(".")[1] | @base64d | fromjson | .realm_access.roles}'
```

---

## Backend Kullanıcı Profili Sync

LDAP kullanıcısı ilk backend isteğinde `UserService.findOrCreateCurrentUser()` çalışır:

1. JWT `sub` ile DB'de kullanıcı ara
2. Bulamazsa email ile ara
3. Hâlâ bulamazsa JWT claim'lerinden otomatik profil oluştur:
   - `keycloakId` = JWT `sub`
   - `email` = JWT `email`
   - `fullName` = JWT `name` (`LDAP Test`)
   - `role` = JWT `realm_access.roles` → `USER`

Backend kodu değişmemiştir. Mekanizma LDAP kullanıcılarını otomatik destekler.

---

## Kullanıcı Lifecycle Davranışları

### Parola Değişikliği / Forgot Password
`editMode: WRITABLE` ile Keycloak, yeni parolayı LDAP'a yazabilir.

- Keycloak üzerinden parola değişikliği → LDAP `userPassword` güncellenir ✅
- Forgot password email akışı → link tıklanınca yeni şifre LDAP'a yazılır ✅
- `ldappasswd` ile doğrudan LDAP'tan da değiştirilebilir:

```bash
docker exec finance_portal_openldap ldappasswd \
  -H ldap://localhost \
  -D "cn=admin,dc=financeportal,dc=local" \
  -w "${LDAP_ADMIN_PASSWORD:-ldapadmin123}" \
  "uid=ldaptest,ou=users,dc=financeportal,dc=local" \
  -s "yeniparola"
```

### OTP / 2FA
Realm seviyesinde zorunlu değil. Keycloak katmanında yönetilir; etkinleştirilebilir.

### Email Doğrulama
`trustEmail: true` — LDAP'tan gelen email'ler otomatik doğrulanmış sayılır.

### Mevcut Keycloak Kullanıcıları
`testuser`, `premiumuser`, `adminuser` etkilenmez; yerel depolama paralel çalışır.

---

## Bilinçli Kısıtlar

| Konu | Davranış |
|------|----------|
| Kayıt: `sn` (Last Name) | `inetOrgPerson` için zorunlu. Kayıt formunda Last Name girilmeli; boş bırakılırsa LDAP entry oluşturulamaz |
| Kullanıcı silme | Keycloak'tan silinen kullanıcı LDAP'tan otomatik silinmez; LDAP'tan ayrıca kaldırılmalı |
| `cn=admin` bind | Dev için yeterli; production'da ACL kısıtlı service account kullanılmalı |
| `username` immutable | `uid` DN'de kullanılır, sonradan değiştirilemez |

---

## Volume Sıfırlama

```bash
docker compose down
docker volume rm infra_openldap_data infra_openldap_config
docker compose up -d
```
