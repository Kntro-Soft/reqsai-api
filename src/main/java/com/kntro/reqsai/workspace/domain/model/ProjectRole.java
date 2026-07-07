package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@Entity
@Table(name = "project_roles")
public class ProjectRole extends AggregateRoot {

    private static final int NAME_MAX = 100;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Convert(converter = com.kntro.reqsai.workspace.infrastructure.persistence.converters.PermissionSetConverter.class)
    @Column(name = "permissions", nullable = false, length = 1000)
    private Set<Permission> permissions;

    protected ProjectRole() {
        super();
    }

    public ProjectRole(UUID projectId, String name, Set<Permission> permissions) {
        super();
        this.projectId = Assert.notNull(projectId, "projectId");
        this.name = normalizeName(name);
        this.permissions = Set.copyOf(Assert.notEmpty(permissions, "permissions"));
    }

    public void update(String name, Set<Permission> permissions) {
        this.name = normalizeName(name);
        this.permissions = Set.copyOf(Assert.notEmpty(permissions, "permissions"));
    }

    public static String normalizeName(String name) {
        return Assert.maxLength(Assert.notBlank(name, "name"), "name", NAME_MAX);
    }
}
