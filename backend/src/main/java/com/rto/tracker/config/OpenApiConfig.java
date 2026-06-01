package com.rto.tracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:}")
    private String baseUrl;

    @Bean
    public OpenAPI rtoTrackerOpenAPI() {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("RTO Tracker API")
                        .description("Commute and Return-to-Office tracking system. " +
                                "Receives zone entry/exit webhooks from Home Assistant and tracks " +
                                "compliance with a quarterly RTO policy.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("X-API-Key"))
                .components(new Components()
                        .addSecuritySchemes("X-API-Key", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("API key for authentication. Pass as X-API-Key header.")));

        if (baseUrl != null && !baseUrl.isBlank()) {
            openAPI.servers(List.of(new Server().url(baseUrl).description("Primary")));
        }

        return openAPI;
    }
}
