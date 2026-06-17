package com.kntro.reqsai.shared.infrastructure.documentation.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * OpenAPI / Swagger UI metadata for the Reqs-AI API.
 * <p>
 * Conditionally enabled by {@code springdoc.api-docs.enabled} (default {@code true}); production sets
 * it to {@code false} (see {@code application-prod.yml}), so this bean — and the docs — are absent in
 * prod. Declares a global <strong>Bearer JWT</strong> security scheme so Swagger UI's "Authorize"
 * button works, plus API info (contact, license, version) and the server URL.
 * <p>
 * Endpoints document their error responses with the composite annotations in the
 * {@code documentation.openapi.annotations} package (e.g. {@code @ApiStandardErrorResponses}).
 * <p>
 * Access (dev): {@code /swagger-ui.html}.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfiguration {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Value("${reqsai.url:http://localhost:8080}")
    private String serverUrl;

    @Value("${reqsai.name:Reqs-AI}")
    private String appName;

    @Bean
    public OpenAPI reqsaiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(appName + " API")
                        .description("AI-powered B2B SaaS requirements elicitation platform")
                        .version("v0")
                        .contact(new Contact().name("Kntro-Soft").email("jhosepmyrgutierrezsoto@gmail.com"))
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(new Server().url(serverUrl).description("Current environment")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token (RS256). Paste the token without the 'Bearer ' prefix.")));
    }

    @Bean
    public GroupedOpenApi workspaceApi() {
        return GroupedOpenApi.builder()
                .group("workspace")
                .packagesToScan("com.kntro.reqsai.workspace.interfaces")
                .build();
    }

    @Bean
    public GroupedOpenApi discoveryApi() {
        return GroupedOpenApi.builder()
                .group("discovery")
                .packagesToScan("com.kntro.reqsai.discovery.interfaces")
                .build();
    }

    @Bean
    @Profile("dev")
    public GroupedOpenApi devToolsApi() {
        return GroupedOpenApi.builder()
                .group("dev-tools")
                .packagesToScan("com.kntro.reqsai.shared.infrastructure.devtools")
                .build();
    }
}
