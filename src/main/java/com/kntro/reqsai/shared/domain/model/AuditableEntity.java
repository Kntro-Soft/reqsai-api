package com.kntro.reqsai.shared.domain.model;

import com.kntro.reqsai.shared.domain.support.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base for every persistent object with identity — both aggregate roots and the non-root entities
 * <em>inside</em> an aggregate.
 * <p>
 * Provides a native {@code uuid} primary key generated as <strong>UUID v7</strong> in the constructor
 * ({@link IdGenerator}), JPA auditing ({@code createdAt/updatedAt/createdBy/updatedBy}; {@code updatedAt}
 * is owned by {@code @LastModifiedDate} — never set it by hand), and identity-based equality. Only
 * {@code @Getter} is exposed: the {@code id} is immutable (no setter); JPA populates fields via reflection.
 * <p>
 * <strong>Use it directly</strong> for non-root entities (loaded/saved through their aggregate root,
 * no repository, no events). Use {@link AggregateRoot} (which extends this) for aggregate roots.
 * <p>
 * Soft-delete is <strong>not</strong> applied here — it is opt-in per aggregate (annotate the concrete
 * entity with {@code @org.hibernate.annotations.SoftDelete}); see CONTRIBUTING.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class AuditableEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;

    /** Creates a new entity with a fresh UUID v7 identity. */
    protected AuditableEntity() {
        this.id = IdGenerator.newId();
    }

    /** Rehydration constructor (reconstructing from persistence with a known id). */
    protected AuditableEntity(UUID id) {
        this.id = id;
    }

    /** Identity-based equality: equal if the same concrete type and the same id. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AuditableEntity that = (AuditableEntity) o;
        return id != null && id.equals(that.id);
    }

    /** Stable hash on the id (assigned at construction, never null afterward). */
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
}
