package com.kntro.reqsai.codebase.application.handler;

import com.kntro.reqsai.codebase.application.port.CodeHostPort;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.RepositoryArchive;
import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort.ProfileDigest;
import com.kntro.reqsai.codebase.application.port.CodeSummaryPort.ProjectProfile;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort;
import com.kntro.reqsai.codebase.application.port.GitHubAppPort.AppRepository;
import com.kntro.reqsai.codebase.application.query.ProposeProjectProfileQuery;
import com.kntro.reqsai.codebase.application.service.RepositoryAccess;
import com.kntro.reqsai.shared.application.ai.AiQuotaGuard;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Application: proposing a new project's profile from its sources")
class ProposeProjectProfileQueryHandlerTest {

    @Mock
    private RepositoryAccess access;
    @Mock
    private GitHubAppPort app;
    @Mock
    private CodeHostPort host;
    @Mock
    private CodeSummaryPort summary;
    @Mock
    private AiQuotaGuard quota;

    private ProposeProjectProfileQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProposeProjectProfileQueryHandler(access, app, host, summary, quota);
        lenient().when(app.repositories(7L)).thenReturn(List.of(
                new AppRepository("acme", "te-tengo-general-api", "main", "https://github.com/acme/te-tengo-general-api",
                        true, "Backend API de Te Tengo", null, "Java"),
                new AppRepository("acme", "te-tengo-mobile-flutter", "main", "https://github.com/acme/te-tengo-mobile-flutter",
                        true, null, null, "Dart")));
        lenient().when(access.tokenFor(7L)).thenReturn("tok");
        lenient().when(host.headCommit(any(), any(), any(), any())).thenReturn("abc123");
        lenient().when(host.download(any(), any(), any(), any(), any(), any())).thenReturn(new RepositoryArchive(List.of(
                new SourceFile("README.md", "# Te Tengo\nControl de gastos del hogar"),
                new SourceFile("docs/README.md", "deeper readme"),
                new SourceFile("build.gradle.kts", "plugins { id(\"org.springframework.boot\") }")), 0, false));
    }

    private static ProposeProjectProfileQuery query() {
        return new ProposeProjectProfileQuery(List.of(
                new ProposeProjectProfileQuery.Repository(7L, "acme/te-tengo-general-api"),
                new ProposeProjectProfileQuery.Repository(7L, "acme/te-tengo-mobile-flutter")), null, null, "es-PE");
    }

    @Test
    @DisplayName("gives the model every repository's README and manifests and returns its proposal")
    void asks_the_model() {
        when(summary.isAvailable()).thenReturn(true);
        when(summary.proposeProfile(any())).thenReturn(new ProjectProfile("Te Tengo", "Control de gastos del hogar",
                "Finanzas personales", "Microservicios", List.of("Java", "Dart"), List.of("Spring Boot", "Flutter"),
                List.of("Android", "iOS"), List.of("PostgreSQL")));

        var proposal = handler.handle(query());

        assertThat(proposal.generated()).isTrue();
        assertThat(proposal.name()).isEqualTo("Te Tengo");
        assertThat(proposal.frameworks()).containsExactly("Spring Boot", "Flutter");
        ArgumentCaptor<ProfileDigest> digest = ArgumentCaptor.forClass(ProfileDigest.class);
        verify(summary).proposeProfile(digest.capture());
        assertThat(digest.getValue().language()).isEqualTo("Spanish");
        assertThat(digest.getValue().repositories()).hasSize(2);
        var first = digest.getValue().repositories().get(0);
        assertThat(first.fullName()).isEqualTo("acme/te-tengo-general-api");
        assertThat(first.description()).isEqualTo("Backend API de Te Tengo");
        assertThat(first.language()).isEqualTo("Java");
        assertThat(first.readme()).contains("Control de gastos del hogar").doesNotContain("deeper");
        assertThat(first.manifests()).contains("build.gradle.kts");
    }

    @Test
    @DisplayName("answers from the metadata when no model is available")
    void falls_back_without_a_model() {
        when(summary.isAvailable()).thenReturn(false);

        var proposal = handler.handle(query());

        assertThat(proposal.generated()).isFalse();
        assertThat(proposal.name()).isEqualTo("te-tengo-general-api");
        assertThat(proposal.description()).isEqualTo("Backend API de Te Tengo");
        assertThat(proposal.programmingLanguages()).containsExactly("Java", "Dart");
        verify(summary, never()).proposeProfile(any());
    }

    @Test
    @DisplayName("answers from the metadata when the model's reply cannot be read")
    void falls_back_on_unreadable_reply() {
        when(summary.isAvailable()).thenReturn(true);
        when(summary.proposeProfile(any())).thenThrow(new IllegalStateException("no JSON"));

        var proposal = handler.handle(query());

        assertThat(proposal.generated()).isFalse();
        assertThat(proposal.programmingLanguages()).containsExactly("Java", "Dart");
    }

    @Test
    @DisplayName("prefers the Jira project's name and description when there is one")
    void jira_leads_the_fallback() {
        when(summary.isAvailable()).thenReturn(false);

        var proposal = handler.handle(new ProposeProjectProfileQuery(
                List.of(new ProposeProjectProfileQuery.Repository(7L, "acme/te-tengo-general-api")),
                "Te Tengo", "Gastos del hogar", "es"));

        assertThat(proposal.name()).isEqualTo("Te Tengo");
        assertThat(proposal.description()).isEqualTo("Gastos del hogar");
    }

    @Test
    @DisplayName("still describes a repository whose files cannot be read, by its metadata")
    void unreadable_repository() {
        when(host.headCommit(any(), any(), any(), any())).thenThrow(new IllegalStateException("host down"));
        when(summary.isAvailable()).thenReturn(false);

        var proposal = handler.handle(query());

        assertThat(proposal.name()).isEqualTo("te-tengo-general-api");
    }

    @Test
    @DisplayName("refuses before reading anything when the AI allowance is spent")
    void refuses_when_quota_spent() {
        doThrow(new DomainException(com.kntro.reqsai.discovery.domain.exception.DiscoveryError.SHARE_LINK_UNAVAILABLE, "spent"))
                .when(quota).assertAvailable();

        assertThatThrownBy(() -> handler.handle(query())).isInstanceOf(DomainException.class);

        verifyNoInteractions(app, host, summary);
    }

    @Test
    @DisplayName("names the language of the proposal after the tag")
    void language_names() {
        assertThat(ProposeProjectProfileQueryHandler.languageName("en-US")).isEqualTo("English");
        assertThat(ProposeProjectProfileQueryHandler.languageName("es")).isEqualTo("Spanish");
        assertThat(ProposeProjectProfileQueryHandler.languageName(null)).isEqualTo("Spanish");
    }
}
