package com.kntro.reqsai.discovery.domain.model;

import com.kntro.reqsai.shared.domain.model.AuditableEntity;
import com.kntro.reqsai.shared.domain.support.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The bytes of the recording a session was created from, kept apart from the
 * {@link DiscoverySession} row so that listing sessions never reads them.
 * Non-root entity owned by one session ({@code sessionId} is unique): written
 * when the recording is uploaded and deleted by the retention purge; the
 * session itself records when.
 */
@Entity
@Table(name = "session_audio_contents")
@Getter
public class SessionAudioContent extends AuditableEntity {

    private static final int FILE_NAME_MAX = 255;

    @Column(name = "session_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "file_name", length = FILE_NAME_MAX)
    private @Nullable String fileName;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    protected SessionAudioContent() {
        super();
    }

    public SessionAudioContent(UUID sessionId, @Nullable String fileName, byte[] content) {
        super();
        this.sessionId = Assert.notNull(sessionId, "sessionId");
        this.content = Assert.notNull(content, "content");
        Assert.isTrue(content.length > 0, "content", "must not be empty");
        this.sizeBytes = content.length;
        this.fileName = fileName == null || fileName.isBlank()
                ? null
                : Assert.maxLength(fileName, "fileName", FILE_NAME_MAX);
    }
}
