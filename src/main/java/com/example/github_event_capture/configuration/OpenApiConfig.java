package com.example.github_event_capture.configuration;


import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.info.Info;


@Configuration
public class OpenApiConfig {

    private SecurityScheme JwtScheme () {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }

    @Bean
    public OpenAPI customOpenApi() {
        return new OpenAPI()
                .info(new Info().title("github-event-capture"))
                .components(new Components().addSecuritySchemes(
                        "Bearer Authentication", JwtScheme()
                ))
                .info(new Info().title("github-event-capture"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"));
    }
}
