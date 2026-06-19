package com.kntro.reqsai.workspace.mothers;

import com.kntro.reqsai.workspace.application.command.CreateProjectCommand;
import java.util.List;
import java.util.UUID;

public final class CreateProjectCommandMother {

    private CreateProjectCommandMother() {
    }

    public static CreateProjectCommand valid() {
        return new CreateProjectCommand(
                UUID.randomUUID(),
                "Acme Project",
                "A description",
                List.of("Java"),
                List.of("Spring Boot"),
                List.of("Web"),
                List.of("PostgreSQL"),
                "Clean Architecture",
                "Fintech",
                UUID.randomUUID()
        );
    }

    public static CreateProjectCommand withOrganizationId(UUID organizationId) {
        return new CreateProjectCommand(
                organizationId,
                "Acme Project",
                "A description",
                List.of("Java"),
                List.of("Spring Boot"),
                List.of("Web"),
                List.of("PostgreSQL"),
                "Clean Architecture",
                "Fintech",
                UUID.randomUUID()
        );
    }

    public static CreateProjectCommand withName(String name) {
        return new CreateProjectCommand(
                UUID.randomUUID(),
                name,
                "A description",
                List.of("Java"),
                List.of("Spring Boot"),
                List.of("Web"),
                List.of("PostgreSQL"),
                "Clean Architecture",
                "Fintech",
                UUID.randomUUID()
        );
    }
}
