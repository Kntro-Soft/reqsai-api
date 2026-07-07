package com.kntro.reqsai.workspace.domain.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * An immutable value object record representing the project's technology stack.
 * Used to enrich the LLM context during user story and acceptance criteria generation.
 *
 * <p>The whole profile is optional context: a project may be created with only a name. A missing
 * list is normalized to an empty list (never null), and a blank single-value field is normalized to
 * null. The generation prompt skips whichever parts are empty.
 *
 * @param programmingLanguages Lenguajes de programación principales del stack (ej. "Java", "TypeScript").
 * @param frameworks           Frameworks y bibliotecas relevantes en el proyecto (ej. "Spring Boot", "Next.js").
 * @param clientPlatforms      Plataformas cliente desde las cuales los usuarios finales acceden a la aplicación (ej. "Web", "Mobile iOS", "Mobile Android", "CLI").
 * @param databases            Sistemas de base de datos o motores de persistencia (ej. "PostgreSQL", "Redis", "MongoDB").
 * @param architecture         Estilo arquitectónico del proyecto (ej. "Clean Architecture", "Microservices", "Event-Driven").
 * @param domain               Dominio del negocio o industria de la aplicación (ej. "Fintech", "Healthcare", "E-commerce").
 */
@Embeddable
public record TechnicalProfile(
        @Column(name = "programming_languages")
        @JdbcTypeCode(SqlTypes.ARRAY)
        List<String> programmingLanguages,

        @Column(name = "frameworks")
        @JdbcTypeCode(SqlTypes.ARRAY)
        List<String> frameworks,

        @Column(name = "client_platforms")
        @JdbcTypeCode(SqlTypes.ARRAY)
        List<String> clientPlatforms,

        @Column(name = "databases")
        @JdbcTypeCode(SqlTypes.ARRAY)
        List<String> databases,

        @Column(name = "architecture")
        @Nullable String architecture,

        @Column(name = "domain")
        @Nullable String domain
) {
    public TechnicalProfile {
        programmingLanguages = programmingLanguages == null ? List.of() : List.copyOf(programmingLanguages);
        frameworks = frameworks == null ? List.of() : List.copyOf(frameworks);
        clientPlatforms = clientPlatforms == null ? List.of() : List.copyOf(clientPlatforms);
        databases = databases == null ? List.of() : List.copyOf(databases);
        architecture = architecture == null || architecture.isBlank() ? null : architecture.strip();
        domain = domain == null || domain.isBlank() ? null : domain.strip();
    }

    /** An empty profile, for projects created with only a name. */
    public static TechnicalProfile empty() {
        return new TechnicalProfile(List.of(), List.of(), List.of(), List.of(), null, null);
    }
}
