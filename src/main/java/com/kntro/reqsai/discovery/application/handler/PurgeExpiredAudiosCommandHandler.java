package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.PurgeExpiredAudiosCommand;
import com.kntro.reqsai.discovery.application.port.AudioStoragePort;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Deletes one batch of expired audios of the <strong>current tenant</strong>
 * (the caller binds it; see {@code PurgeExpiredAudiosService}). For each
 * session whose audio is past its retention period and which is not in
 * progress, the stored bytes are dropped and the session is stamped as purged;
 * its transcript, segments and stories are left alone. The whole batch is one
 * transaction, so the bytes and the purge mark never disagree, and a purged
 * session no longer matches the candidate query, which makes a repeated run a
 * no-op.
 */
@Component
@RequiredArgsConstructor
public class PurgeExpiredAudiosCommandHandler {

    private final DiscoverySessionRepository sessions;
    private final AudioStoragePort audioStorage;

    /** @return how many audios were deleted in this batch */
    @Transactional
    public int handle(PurgeExpiredAudiosCommand command) {
        List<DiscoverySession> due = sessions.findAudioPurgeCandidates(command.storedBefore(), command.batchSize());
        int purged = 0;
        for (DiscoverySession session : due) {
            if (!session.isAudioPurgeable(command.storedBefore())) {
                continue;
            }
            audioStorage.delete(session.getId());
            session.purgeAudio(command.now());
            sessions.save(session);
            purged++;
        }
        return purged;
    }
}
