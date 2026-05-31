package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException.PremiumRequiredException;
import com.emrehalli.financeportal.user.entity.UserRole;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PremiumAccessAspect {

    private static final Logger logger = LogManager.getLogger(PremiumAccessAspect.class);

    private final KeycloakJwtRoleConverter keycloakJwtRoleConverter;

    public PremiumAccessAspect(KeycloakJwtRoleConverter keycloakJwtRoleConverter) {
        this.keycloakJwtRoleConverter = keycloakJwtRoleConverter;
    }

    @Around("@annotation(com.emrehalli.financeportal.config.security.RequiresPremium) || " +
            "@within(com.emrehalli.financeportal.config.security.RequiresPremium)")
    public Object checkPremiumAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new PremiumRequiredException();
        }

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new PremiumRequiredException();
        }

        UserRole role = keycloakJwtRoleConverter.extractRole(jwt);
        if (role != UserRole.USER_PREMIUM && role != UserRole.ADMIN) {
            logger.info("Premium eriÅŸim reddedildi: role={}, method={}",
                    role, joinPoint.getSignature().toShortString());
            throw new PremiumRequiredException();
        }

        return joinPoint.proceed();
    }
}

