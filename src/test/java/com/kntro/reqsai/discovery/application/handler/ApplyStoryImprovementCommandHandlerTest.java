package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ApplyStoryImprovementCommand;
import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.service.StoryQualityAssessor;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ApplyStoryImprovementCommandHandler} with mocked ports and a real {@link UserStory}. */
@DisplayName("Application: Apply an AI improvement of a story")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplyStoryImprovementCommandHandlerTest {

    private static final UUID PROJECT = UUID.randomUUID();

    @Mock private UserStoryRepository stories;
    @Mock private StoryQualityAssessor qualityAssessor;

    private ApplyStoryImprovementCommandHandler handler;
    private UserStory story;
    private final StoryQualityAssessment assessment = mock(StoryQualityAssessment.class);

    @BeforeEach
    void setUp() {
        handler = new ApplyStoryImprovementCommandHandler(stories, qualityAssessor);
        story = new UserStory(PROJECT, "Exportar", "usuario", "exportar el backlog de forma rápida",
                "mejor control, etc.", Priority.HIGH, 5);
        story.addAcceptanceCriterion("viejo", "dado viejo", "cuando viejo", "entonces viejo");
        story.addAcceptanceCriterion(null, "dado viejo 2", "cuando viejo 2", "entonces viejo 2");
        story.captureGeneratedText("Exportar\nusuario\nexportar el backlog de forma rápida\nmejor control, etc.");
        story.changeReviewStatus(StoryStatus.APPROVED);
        when(stories.findByIdAndProjectId(story.getId(), PROJECT)).thenReturn(Optional.of(story));
        when(stories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(qualityAssessor.evaluate(any())).thenReturn(assessment);
    }

    private ApplyStoryImprovementCommand command(StoryDraft draft) {
        return new ApplyStoryImprovementCommand(PROJECT, story.getId(), draft);
    }

    private static StoryDraft improved() {
        return new StoryDraft("Exportar el backlog a CSV", "analista de cumplimiento",
                "exportar el backlog aprobado a un archivo CSV", "compartirlo con el cliente sin reescribirlo",
                List.of(new StoryDraft.CriterionDraft("camino feliz", "hay historias aprobadas",
                        "el analista exporta el backlog", "el sistema descarga un archivo CSV")));
    }

    @Test
    @DisplayName("replaces the text and the criteria, keeps priority, estimate and status, and evaluates the saved story")
    void applies_the_proposal() {
        AppliedStoryImprovement result = handler.handle(command(improved()));

        assertThat(result.story().getTitle()).isEqualTo("Exportar el backlog a CSV");
        assertThat(result.story().getRole()).isEqualTo("analista de cumplimiento");
        assertThat(result.story().getAction()).isEqualTo("exportar el backlog aprobado a un archivo CSV");
        assertThat(result.story().getBenefit()).isEqualTo("compartirlo con el cliente sin reescribirlo");
        assertThat(result.story().getAcceptanceCriteria()).hasSize(1);
        assertThat(result.story().getAcceptanceCriteria().getFirst().getGiven()).isEqualTo("hay historias aprobadas");
        assertThat(result.story().getAcceptanceCriteria().getFirst().getScenario()).isEqualTo("camino feliz");
        // Like a manual update: priority, estimate and the review decision are not touched.
        assertThat(result.story().getPriority()).isEqualTo(Priority.HIGH);
        assertThat(result.story().getStoryPoints()).isEqualTo(5);
        assertThat(result.story().getStatus()).isEqualTo(StoryStatus.APPROVED);
        assertThat(result.quality()).isSameAs(assessment);
        var order = inOrder(stories, qualityAssessor);
        order.verify(stories).save(story);
        order.verify(qualityAssessor).evaluate(story);
    }

    @Test
    @DisplayName("does not touch the generated text, so the applied improvement counts as a rewrite")
    void generated_text_is_untouched() {
        String generated = story.getGeneratedText();

        handler.handle(command(improved()));

        assertThat(story.getGeneratedText()).isEqualTo(generated);
        assertThat(story.currentText()).isNotEqualTo(generated);
    }

    @Test
    @DisplayName("a proposal without criteria removes the story's criteria")
    void empty_criteria_replace_the_old_ones() {
        StoryDraft draft = improved();

        handler.handle(command(new StoryDraft(draft.title(), draft.role(), draft.action(), draft.benefit(), List.of())));

        assertThat(story.getAcceptanceCriteria()).isEmpty();
    }

    @Test
    @DisplayName("a token left in any field answers IMPROVEMENT_PENDING_VALUES and changes nothing")
    void pending_values_are_refused() {
        StoryDraft draft = new StoryDraft("Exportar", "analista", "exportar", "compartir [[para quién]]",
                List.of(new StoryDraft.CriterionDraft(null, "dado", "cuando", "responde en [[tiempo]] segundos")));

        assertThatThrownBy(() -> handler.handle(command(draft)))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.error()).isEqualTo(DiscoveryError.IMPROVEMENT_PENDING_VALUES);
                    assertThat(e.getMessage()).contains("[[para quién]]").contains("[[tiempo]]");
                });
        assertThat(story.getTitle()).isEqualTo("Exportar");
        assertThat(story.getAcceptanceCriteria()).hasSize(2);
        verify(stories, never()).save(any());
        verify(qualityAssessor, never()).evaluate(any());
    }

    @Test
    @DisplayName("a story that is not in the project is a 404")
    void story_not_found() {
        UUID other = UUID.randomUUID();
        when(stories.findByIdAndProjectId(other, PROJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ApplyStoryImprovementCommand(PROJECT, other, improved())))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(DiscoveryError.USER_STORY_NOT_FOUND));
    }

    @Test
    @DisplayName("the domain invariants still hold: a blank or over-long field is rejected")
    void domain_invariants_hold() {
        StoryDraft draft = improved();

        assertThatThrownBy(() -> handler.handle(command(new StoryDraft("t".repeat(201), draft.role(), draft.action(),
                draft.benefit(), draft.acceptanceCriteria()))))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> handler.handle(command(new StoryDraft(draft.title(), " ", draft.action(),
                draft.benefit(), draft.acceptanceCriteria()))))
                .isInstanceOf(DomainException.class);
    }
}
