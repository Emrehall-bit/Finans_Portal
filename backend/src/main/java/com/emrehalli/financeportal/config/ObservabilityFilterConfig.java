package com.emrehalli.financeportal.config;

import com.emrehalli.financeportal.common.logging.HttpRequestLoggingFilter;
import com.emrehalli.financeportal.common.logging.RequestCorrelationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ObservabilityFilterConfig {

    @Bean
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilterRegistration() {
        FilterRegistrationBean<RequestCorrelationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestCorrelationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("requestCorrelationFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<HttpRequestLoggingFilter> httpRequestLoggingFilterRegistration() {
        FilterRegistrationBean<HttpRequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new HttpRequestLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.setName("httpRequestLoggingFilter");
        return registration;
    }
}
