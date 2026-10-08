package com.kntro.reqsai.discovery.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link SpeakerRoster}: numbering by first appearance and merging the analyst's descriptions. */
@DisplayName("Domain: SpeakerRoster")
class SpeakerRosterTest {

    private static final UUID SESSION = UUID.randomUUID();

    @Test
    @DisplayName("numbers speakers by first appearance as \"Hablante N\" and counts their segments")
    void numbers_by_first_appearance() {
        SpeakerRoster roster = SpeakerRoster.of(List.of(
                new SpeakerSpan("B", 0, 1_000),
                new SpeakerSpan("A", 1_000, 2_000),
                new SpeakerSpan("B", 2_000, 3_000),
                new SpeakerSpan("C", 3_000, 4_000)), List.of());

        assertThat(roster.speakers()).extracting(SpeakerRoster.Speaker::label).containsExactly("B", "A", "C");
        assertThat(roster.speakers()).extracting(SpeakerRoster.Speaker::name)
                .containsExactly("Hablante 1", "Hablante 2", "Hablante 3");
        assertThat(roster.speakers()).extracting(SpeakerRoster.Speaker::segmentCount).containsExactly(2, 1, 1);
        assertThat(roster.speakers()).allMatch(s -> s.side() == null && s.displayName() == null);
    }

    @Test
    @DisplayName("uses the analyst's name and side, keeping the index of first appearance")
    void merges_descriptions() {
        SessionSpeaker team = new SessionSpeaker(SESSION, "1");
        team.describe("Luis", SpeakerSide.TEAM);
        SessionSpeaker ghost = new SessionSpeaker(SESSION, "9");
        ghost.describe("Nadie", SpeakerSide.CLIENT);

        SpeakerRoster roster = SpeakerRoster.of(List.of(
                new SpeakerSpan("0", 0, 1_000), new SpeakerSpan("1", 1_000, 2_000)), List.of(team, ghost));

        SpeakerRoster.Speaker luis = roster.find("1").orElseThrow();
        assertThat(luis.index()).isEqualTo(2);
        assertThat(luis.name()).isEqualTo("Luis");
        assertThat(luis.side()).isEqualTo(SpeakerSide.TEAM);
        assertThat(roster.find("9")).as("a description of a label that never spoke is ignored").isEmpty();
        assertThat(roster.speakers()).hasSize(2);
    }

    @Test
    @DisplayName("resolves a label not in the roster yet as the next unnamed speaker")
    void resolves_new_label() {
        SpeakerRoster roster = SpeakerRoster.of(List.of(new SpeakerSpan("0", 0, 1_000)), List.of());

        assertThat(roster.resolve("0").index()).isEqualTo(1);
        assertThat(roster.resolve("7").name()).isEqualTo("Hablante 2");
        assertThat(SpeakerRoster.empty().isEmpty()).isTrue();
        assertThat(roster.find(null)).isEmpty();
    }
}
