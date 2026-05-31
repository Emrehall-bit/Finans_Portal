package com.emrehalli.financeportal.technicalanalysis.fundamental.service;

import com.emrehalli.financeportal.user.entity.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Temel analiz endpoint'lerinin yetki/erişim kararlarını tek noktada toplayan, state taşımayan policy.
 * Bu kararlar daha önce {@code AnalysisController} içinde gömülüydü; controller'ı yalnızca
 * request/response + delegasyona indirmek ve kuralları izole/test edilebilir kılmak için buraya alındı.
 * Rol karşılaştırmaları ve fırlatılan HTTP status/mesaj, önceki controller davranışıyla birebir aynıdır.
 */
@Component
public class FundamentalAccessPolicy {

    /**
     * Premium içerik (Graham Number, Piotroski, Altman Z vb.) erişim hakkı: {@code USER_PREMIUM} veya
     * {@code ADMIN}. Erişimi bloklamaz; yalnızca response'taki premium alanların dolu gelip
     * gelmeyeceğini belirler (content gating). Bu nedenle bilinçli olarak {@code @RequiresPremium}
     * yerine boolean karar kullanılır — anlamları farklıdır.
     */
    public boolean canAccessPremiumContent(UserRole role) {
        return role == UserRole.USER_PREMIUM || role == UserRole.ADMIN;
    }

    /**
     * İşlemi yalnızca {@code ADMIN} rolüne açar; aksi halde controller'ın önceki davranışıyla birebir
     * aynı 403 FORBIDDEN'ı aynı mesajla fırlatır. {@link ResponseStatusException},
     * {@code GlobalExceptionHandler} tarafından aynı response shape ile işlenir.
     */
    public void requireAdmin(UserRole role) {
        if (role != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu islem sadece admin kullanicilara aciktir");
        }
    }
}
