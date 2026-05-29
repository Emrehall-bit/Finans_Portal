package com.emrehalli.financeportal.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
public class I18nConfig {

    private static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.ENGLISH,
            Locale.forLanguageTag("tr")
    );

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                Locale requestLocale = resolveRequestedLocale(request);
                return requestLocale != null ? requestLocale : Locale.ENGLISH;
            }

            @Override
            public void setLocale(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, Locale locale) {
                throw new UnsupportedOperationException("Request-scoped locale only");
            }
        };
    }

    private Locale resolveRequestedLocale(HttpServletRequest request) {
        String explicitLanguage = firstNonBlank(
                request.getParameter("lang"),
                request.getHeader("X-Language")
        );
        if (explicitLanguage != null) {
            Locale explicitLocale = normalizeLocale(explicitLanguage);
            if (explicitLocale != null) {
                return explicitLocale;
            }
        }

        if (!request.getLocales().hasMoreElements()) {
            return null;
        }

        return findSupported(request.getLocales().nextElement());
    }

    private Locale normalizeLocale(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "tr", "tr-tr", "turkish", "turkce" -> Locale.forLanguageTag("tr");
            case "en", "en-us", "en-gb", "english" -> Locale.ENGLISH;
            default -> null;
        };
    }

    private Locale findSupported(Locale locale) {
        if (locale == null) {
            return null;
        }

        for (Locale supportedLocale : SUPPORTED_LOCALES) {
            if (supportedLocale.getLanguage().equalsIgnoreCase(locale.getLanguage())) {
                return supportedLocale;
            }
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}




