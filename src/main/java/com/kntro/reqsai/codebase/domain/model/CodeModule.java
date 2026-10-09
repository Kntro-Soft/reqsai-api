package com.kntro.reqsai.codebase.domain.model;

import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One part of a connected repository (a folder of source files) as the copilot knows it: what it does,
 * the capabilities it offers, the business rules it implements, and its endpoints and entities. Built
 * from the code while indexing; the code itself is not kept. The {@code contentHash} lets a reindex skip
 * modules whose files did not change.
 */
@Entity
@Table(name = "code_modules")
@Getter
public class CodeModule extends AggregateRoot {

    public static final int NAME_MAX = 200;
    public static final int PATH_MAX = 500;
    public static final int SUMMARY_MAX = 4000;
    private static final int ITEM_MAX = 300;
    private static final int LIST_MAX = 40;

    @Column(name = "repository_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID repositoryId;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "path", nullable = false, length = PATH_MAX, updatable = false)
    private String path;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", columnDefinition = "jsonb", nullable = false)
    private List<String> capabilities = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_rules", columnDefinition = "jsonb", nullable = false)
    private List<String> businessRules = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "endpoints", columnDefinition = "jsonb", nullable = false)
    private List<String> endpoints = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entities", columnDefinition = "jsonb", nullable = false)
    private List<String> entities = new ArrayList<>();

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "summarized", nullable = false)
    private boolean summarized;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EmbeddingPort.DIMENSIONS)
    @Column(name = "embedding")
    private float @Nullable [] embedding;

    protected CodeModule() {
        super();
    }

    public CodeModule(UUID repositoryId, UUID projectId, String path) {
        super();
        this.repositoryId = Assert.notNull(repositoryId, "repositoryId");
        this.projectId = Assert.notNull(projectId, "projectId");
        this.path = Assert.maxLength(Assert.notNull(path, "path"), "path", PATH_MAX);
    }

    /** Replaces what the copilot knows about this module after (re)reading its files. */
    public void describe(String name, String summary, List<String> capabilities, List<String> businessRules,
                         List<String> endpoints, List<String> entities, int fileCount, String contentHash,
                         boolean summarized, float @Nullable [] embedding) {
        this.name = clip(Assert.notBlank(name, "name"), NAME_MAX);
        this.summary = clip(Assert.notBlank(summary, "summary"), SUMMARY_MAX);
        this.capabilities = clipAll(capabilities);
        this.businessRules = clipAll(businessRules);
        this.endpoints = clipAll(endpoints);
        this.entities = clipAll(entities);
        this.fileCount = Math.max(0, fileCount);
        this.contentHash = Assert.notBlank(contentHash, "contentHash");
        this.summarized = summarized;
        this.embedding = embedding;
    }

    /** The text the embedding model reads for similarity search against the meeting transcript. */
    public static String embeddingText(String name, String summary, List<String> capabilities,
                                       List<String> businessRules) {
        StringBuilder sb = new StringBuilder(name).append(". ").append(summary);
        if (!capabilities.isEmpty()) sb.append(" ").append(String.join("; ", capabilities));
        if (!businessRules.isEmpty()) sb.append(" ").append(String.join("; ", businessRules));
        return sb.toString();
    }

    private static String clip(String value, int max) {
        String v = value.strip();
        return v.length() <= max ? v : v.substring(0, max - 3) + "...";
    }

    private static List<String> clipAll(@Nullable List<String> values) {
        if (values == null) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            out.add(clip(value, ITEM_MAX));
            if (out.size() >= LIST_MAX) break;
        }
        return out;
    }
}
