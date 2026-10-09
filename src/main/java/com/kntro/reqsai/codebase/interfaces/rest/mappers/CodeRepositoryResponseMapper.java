package com.kntro.reqsai.codebase.interfaces.rest.mappers;

import com.kntro.reqsai.codebase.domain.model.CodeModule;
import com.kntro.reqsai.codebase.domain.model.CodeProfile;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import com.kntro.reqsai.codebase.domain.model.CodeRepositoryStatus;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.CodeModuleResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.CodeProfileResponse;
import com.kntro.reqsai.codebase.interfaces.rest.dto.response.CodeRepositoryResponse;

import java.time.Instant;

/** Maps the codebase aggregates to their response DTOs. A stalled run reads as failed, so it can be retried. */
public final class CodeRepositoryResponseMapper {

    static final String STALLED = "La indexación se interrumpió; vuelve a intentarlo.";

    private CodeRepositoryResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static CodeRepositoryResponse toResponse(CodeRepository repo) {
        boolean stalled = repo.isStalled(Instant.now(), CodeRepository.STALL_AFTER);
        CodeProfile profile = repo.getProfile() == null ? CodeProfile.empty() : repo.getProfile();
        return new CodeRepositoryResponse(
                repo.getId(),
                repo.getProjectId(),
                repo.getProvider().name(),
                repo.getOwner(),
                repo.getName(),
                repo.fullName(),
                repo.getBranch(),
                repo.getHtmlUrl(),
                repo.isPrivateRepo(),
                repo.hasToken(),
                stalled ? CodeRepositoryStatus.FAILED.name() : repo.getStatus().name(),
                stalled ? STALLED : repo.getError(),
                repo.getCommitSha(),
                repo.getIndexedAt(),
                repo.getFileCount(),
                repo.getModuleCount(),
                repo.getModulesDone(),
                repo.isSummarized(),
                new CodeProfileResponse(profile.languages(), profile.frameworks(), profile.databases(),
                        profile.platforms(), profile.overview()),
                repo.getCreatedAt());
    }

    public static CodeModuleResponse toResponse(CodeModule module, CodeRepository repo) {
        return new CodeModuleResponse(module.getId(), module.getPath(), module.getName(), module.getSummary(),
                module.getCapabilities(), module.getBusinessRules(), module.getEndpoints(), module.getEntities(),
                module.getFileCount(), repo.treeUrl(module.getPath()));
    }
}
