package com.emrehalli.financeportal.config.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bu annotation'ı taşıyan endpoint'ler USER_PREMIUM veya ADMIN rolü gerektirir.
 * PremiumAccessAspect tarafından intercept edilir.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPremium {
}
