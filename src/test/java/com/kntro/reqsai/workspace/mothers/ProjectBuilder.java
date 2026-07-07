package com.kntro.reqsai.workspace.mothers;

import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;
import net.datafaker.Faker;
import java.util.List;
import java.util.UUID;

public class ProjectBuilder {

    private static final Faker FAKER = new Faker();

    private UUID organizationId = UUID.randomUUID();
    private String name = FAKER.company().name();
    private String description = FAKER.lorem().sentence();
    private List<String> programmingLanguages = List.of("Java", "TypeScript");
    private List<String> frameworks = List.of("Spring Boot", "Next.js");
    private List<String> clientPlatforms = List.of("Web", "Mobile");
    private List<String> databases = List.of("PostgreSQL", "Redis");
    private String architecture = "Clean Architecture";
    private String domain = "Fintech";
    private UUID createdBy = UUID.randomUUID();

    public static ProjectBuilder aProject() {
        return new ProjectBuilder();
    }

    public ProjectBuilder withOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
        return this;
    }

    public ProjectBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public ProjectBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public ProjectBuilder withProgrammingLanguages(List<String> programmingLanguages) {
        this.programmingLanguages = programmingLanguages;
        return this;
    }

    public ProjectBuilder withFrameworks(List<String> frameworks) {
        this.frameworks = frameworks;
        return this;
    }

    public ProjectBuilder withClientPlatforms(List<String> clientPlatforms) {
        this.clientPlatforms = clientPlatforms;
        return this;
    }

    public ProjectBuilder withDatabases(List<String> databases) {
        this.databases = databases;
        return this;
    }

    public ProjectBuilder withArchitecture(String architecture) {
        this.architecture = architecture;
        return this;
    }

    public ProjectBuilder withDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public ProjectBuilder withCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public Project build() {
        TechnicalProfile profile = new TechnicalProfile(
                programmingLanguages, frameworks, clientPlatforms, databases, architecture, domain);
        return new Project(organizationId, name, description, profile, createdBy);
    }
}
