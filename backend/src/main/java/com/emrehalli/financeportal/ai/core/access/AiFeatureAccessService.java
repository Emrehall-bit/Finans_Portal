package com.emrehalli.financeportal.ai.core.access;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AiFeatureAccessService {

    private static final Logger logger = LogManager.getLogger(AiFeatureAccessService.class);

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

