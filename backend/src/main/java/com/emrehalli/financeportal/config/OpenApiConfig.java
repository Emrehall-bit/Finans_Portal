package com.emrehalli.financeportal.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI financePortalOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Finans Portal API")
                        .version("1.0.0")
                        .description("Finans Portal uygulamasının kimlik doğrulama, kullanıcı yönetimi, " +
                                "portföy, piyasa verileri, haber, premium abonelik ve admin işlemlerini " +
                                "kapsayan REST API dokümantasyonu.")
                        .contact(new Contact()
                                .name("Finans Portal")
                                .email("ehalli460@gmail.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak JWT Bearer Token")));
    }
}
