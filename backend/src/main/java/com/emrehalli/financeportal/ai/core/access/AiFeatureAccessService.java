package com.emrehalli.financeportal.ai.core.access;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service for checking and logging AI feature access.
 *
 * Enforcement is handled at the HTTP layer by Spring Security (SecurityConfig).
 * This service adds:
 *   - Audit logging for both granted and denied premium access.
 *   - A programmatic {@link #hasAccess} helper for service-layer guards.
 *
 * Call {@link #logAccess} from controllers after a successful request to produce
 * a GRANTED audit log entry. Denied entries are produced by
 * {@link com.emrehalli.financeportal.config.security.AiPremiumAccessDeniedHandler}.
 */
@Service
public class AiFeatureAccessService {

    private static final Logger logger = LogManager.getLogger(AiFeatureAccessService.class);

    /**
     * Returns true if the current security context grants access to the given feature.
     * Free features always return true. Premium features require ROLE_USER_PREMIUM or ROLE_ADMIN.
     */
    public boolean hasAccess(AiFeatureType feature) {
        if (!feature.isPremiumRequired()) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_USER_PREMIUM".equals(a.getAuthority())
                            || "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Logs a successful (GRANTED) AI feature access.
     * Should be called from controllers once the request has been allowed through.
     */
    public void logAccess(AiFeatureType feature) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth != null ? auth.getName() : "anonymous";
        if (feature.isPremiumRequired()) {
            logger.info("Premium AI feature access granted. feature={}, user={}, result=GRANTED",
                    feature.name(), userId);
        } else {
            logger.debug("Free AI feature accessed. feature={}, user={}", feature.name(), userId);
        }
    }
}




