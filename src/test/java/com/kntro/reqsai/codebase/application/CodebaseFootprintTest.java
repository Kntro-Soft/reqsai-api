package com.kntro.reqsai.codebase.application;

import com.kntro.reqsai.codebase.api.CodeFootprint;
import com.kntro.reqsai.codebase.application.port.CodeModuleRepository;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository.StatusSummary;
import com.kntro.reqsai.codebase.domain.model.CodeRepositoryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Application: connected code footprint")
@ExtendWith(MockitoExtension.class)
class CodebaseFootprintTest {

    @Mock
    private CodeRepositoryRepository repositories;
    @Mock
    private CodeModuleRepository modules;

    /** The implementation is package-private in {@code application.service}; reach it through its constructor. */
    private com.kntro.reqsai.codebase.api.CodebaseModuleApi api() throws Exception {
        Class<?> type = Class.forName("com.kntro.reqsai.codebase.application.service.CodebaseModuleApiImpl");
        Constructor<?> constructor = type.getDeclaredConstructor(CodeRepositoryRepository.class,
                CodeModuleRepository.class);
        constructor.setAccessible(true);
        return (com.kntro.reqsai.codebase.api.CodebaseModuleApi) constructor.newInstance(repositories, modules);
    }

    @Test
    @DisplayName("adds up repositories, files and modules across statuses and keeps the newest indexing")
    void sumsAcrossStatuses() throws Exception {
        Set<UUID> ids = Set.of(UUID.randomUUID(), UUID.randomUUID());
        Instant older = Instant.parse("2026-10-01T10:00:00Z");
        Instant newer = Instant.parse("2026-10-05T10:00:00Z");
        when(repositories.summarizeByStatus(ids)).thenReturn(List.of(
                new StatusSummary(CodeRepositoryStatus.READY, 2, 50, older),
                new StatusSummary(CodeRepositoryStatus.FAILED, 1, 10, newer),
                new StatusSummary(CodeRepositoryStatus.PENDING, 1, 0, null)));
        when(modules.countByProjectIdIn(ids)).thenReturn(9L);

        CodeFootprint footprint = api().footprint(ids);

        assertThat(footprint).isEqualTo(new CodeFootprint(4, 2, 1, 9, 60, newer));
    }

    @Test
    @DisplayName("nothing connected, or no projects, is the empty footprint")
    void none() throws Exception {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        when(repositories.summarizeByStatus(ids)).thenReturn(List.of());

        assertThat(api().footprint(ids)).isEqualTo(CodeFootprint.none());
        assertThat(api().footprint(Set.of())).isEqualTo(CodeFootprint.none());
        verifyNoInteractions(modules);
    }
}
