package com.kntro.reqsai.workspace.domain.valueobjects;

import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;

/**
 * An immutable value object record representing the project's technology stack.
 * Used to enrich the LLM context during user story and acceptance criteria generation.
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

        @Column(name = "architecture", nullable = false)
        String architecture,

        @Column(name = "domain", nullable = false)
        String domain
) {
    public TechnicalProfile {
        Assert.notNull(programmingLanguages, "programmingLanguages");
        Assert.notNull(frameworks, "frameworks");
        Assert.notNull(clientPlatforms, "clientPlatforms");
        Assert.notNull(databases, "databases");
        Assert.notBlank(architecture, "architecture");
        Assert.notBlank(domain, "domain");
    }
}
