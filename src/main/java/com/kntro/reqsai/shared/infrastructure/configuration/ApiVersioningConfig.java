package com.kntro.reqsai.shared.infrastructure.configuration;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enables Spring's native header-based API versioning: the version is read from the {@code Api-Version}
 * header, defaulting to {@link ApiVersioning#V1} when the header is absent.
 */
@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(@NonNull ApiVersionConfigurer configurer) {
        configurer
                .useRequestHeader(ApiVersioning.HEADER)
                .setDefaultVersion(ApiVersioning.V1)
                .addSupportedVersions(ApiVersioning.V1);
    }
}
