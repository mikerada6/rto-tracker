package com.rto.tracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rtoTrackerOpenAPI() {
        return new OpenAPI()
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
    }
}
