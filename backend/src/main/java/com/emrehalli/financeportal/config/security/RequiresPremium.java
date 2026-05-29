package com.emrehalli.financeportal.config.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bu annotation'Ä± taÅŸÄ±yan endpoint'ler USER_PREMIUM veya ADMIN rolÃ¼ gerektirir.
 * PremiumAccessAspect tarafÄ±ndan intercept edilir.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPremium {
}

