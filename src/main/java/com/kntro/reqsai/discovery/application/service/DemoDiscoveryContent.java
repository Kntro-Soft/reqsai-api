package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Static sample discovery content of the demo project (US28), matching the workspace's restaurant
 * scenario: one finished elicitation session with its diarized transcript, the user stories extracted from
 * it (approved and still in review) with Given/When/Then acceptance criteria, and two suggestions left
 * pending for the analyst to decide.
 * <p>
 * {@link #build(UUID)} returns <em>unsaved</em> aggregates created through their own constructors, factories
 * and state transitions, so the demo obeys every domain invariant. No AI is involved: stories are left
 * without an embedding and enter the vector index through the lazy re-index pass.
 */
public final class DemoDiscoveryContent {

    public static final String SESSION_TITLE = "Levantamiento de requisitos con la administradora de La Tradición";
    public static final LanguageCode LANGUAGE = LanguageCode.of("es-PE");

    private static final String ANALYST = "0";
    private static final String CLIENT = "1";

    private static final List<Line> TRANSCRIPT = List.of(
            new Line(ANALYST, "Buenas tardes, Rosa. Gracias por tu tiempo. Cuéntame, ¿cómo reciben hoy las "
                    + "reservas en La Tradición?"),
            new Line(CLIENT, "Todo es por teléfono y por WhatsApp. Los fines de semana se nos cruzan las "
                    + "reservas y a veces damos la misma mesa dos veces."),
            new Line(ANALYST, "Entiendo. ¿Qué les gustaría que el comensal pueda hacer por su cuenta?"),
            new Line(CLIENT, "Que reserve desde la web o el celular eligiendo la fecha, el turno y cuántas "
                    + "personas vienen, y que le llegue la confirmación al toque, por correo o WhatsApp."),
            new Line(ANALYST, "¿Y si el comensal necesita cancelar su reserva?"),
            new Line(CLIENT, "Que pueda cancelarla hasta dos horas antes. Si cancela más tarde o no viene, lo "
                    + "marcamos como no-show para llevar la cuenta."),
            new Line(ANALYST, "¿Cómo controlan hoy la ocupación del salón durante el turno?"),
            new Line(CLIENT, "El anfitrión lo anota todo en un cuaderno. Necesitamos ver en una pantalla qué "
                    + "mesas están libres, reservadas u ocupadas."),
            new Line(ANALYST, "¿Qué pasa cuando el turno ya está lleno?"),
            new Line(CLIENT, "Perdemos clientes. Sería bueno tener una lista de espera que les avise si se "
                    + "libera una mesa."),
            new Line(ANALYST, "Última pregunta: ¿qué datos le pedirían al comensal?"),
            new Line(CLIENT, "Solo nombre, celular y correo, y que acepte que lo contactemos. No queremos "
                    + "pedir más de lo necesario.")
    );

    /** Average spoken length of a transcript line, used to lay the segments out on the timeline. */
    private static final long LINE_DURATION_MS = 28_000;

    private static final List<StoryBlueprint> STORIES = List.of(
            new StoryBlueprint("Reservar mesa en línea", "comensal",
                    "reservar una mesa desde la web o el celular eligiendo la fecha, el turno y el número de "
                            + "personas",
                    "no tener que llamar ni escribir por WhatsApp para asegurar mi lugar",
                    Priority.HIGH, 5, StoryStatus.APPROVED, List.of(
                    new CriterionBlueprint("Reserva con disponibilidad",
                            "hay mesas libres para 4 personas el sábado en el turno de cena",
                            "el comensal elige esa fecha, ese turno y 4 personas y confirma",
                            "la reserva queda confirmada y se le muestra su código de reserva"),
                    new CriterionBlueprint("Turno lleno",
                            "el aforo del turno elegido ya está completo",
                            "el comensal intenta reservar en ese turno",
                            "se le informa que no hay disponibilidad y se le ofrecen otros turnos o la lista "
                                    + "de espera"))),
            new StoryBlueprint("Recibir la confirmación de la reserva", "comensal",
                    "recibir la confirmación de mi reserva por correo o WhatsApp",
                    "tener la certeza de que mi mesa está asegurada",
                    Priority.HIGH, 3, StoryStatus.APPROVED, List.of(
                    new CriterionBlueprint("Confirmación inmediata",
                            "el comensal acaba de confirmar una reserva",
                            "el sistema la registra",
                            "le envía un mensaje con la fecha, el turno, las personas y el código en menos de "
                                    + "1 minuto"),
                    new CriterionBlueprint("Sin consentimiento para WhatsApp",
                            "el comensal no aceptó ser contactado por WhatsApp",
                            "se envía la confirmación",
                            "se envía solo por correo electrónico"))),
            new StoryBlueprint("Cancelar una reserva", "comensal",
                    "cancelar mi reserva desde el enlace de la confirmación",
                    "liberar la mesa si ya no podré asistir",
                    Priority.MEDIUM, 3, StoryStatus.APPROVED, List.of(
                    new CriterionBlueprint("Cancelación a tiempo",
                            "faltan más de 2 horas para la reserva",
                            "el comensal la cancela desde el enlace",
                            "la reserva pasa a cancelada y la mesa vuelve a estar disponible"),
                    new CriterionBlueprint("Cancelación tardía",
                            "faltan menos de 2 horas para la reserva",
                            "el comensal intenta cancelarla",
                            "se le indica que debe comunicarse por teléfono con el restaurante"))),
            new StoryBlueprint("Ver la ocupación del salón en tiempo real", "anfitrión del restaurante",
                    "ver en una pantalla qué mesas están libres, reservadas u ocupadas en el turno actual",
                    "ubicar rápido a los comensales que llegan",
                    Priority.HIGH, 8, StoryStatus.DRAFT, List.of(
                    new CriterionBlueprint("Plano del salón",
                            "el turno de cena está en curso",
                            "el anfitrión abre el plano del salón",
                            "cada mesa muestra su estado con un color distinto"),
                    new CriterionBlueprint("Llegada de un comensal",
                            "llega un comensal con reserva",
                            "el anfitrión marca su mesa como ocupada",
                            "el estado se actualiza en todas las pantallas en menos de 5 segundos"))),
            new StoryBlueprint("Registrar un no-show", "administradora del restaurante",
                    "marcar como no-show las reservas a las que el comensal no asistió",
                    "medir el ausentismo y ajustar la política de reservas",
                    Priority.LOW, 2, StoryStatus.DRAFT, List.of(
                    new CriterionBlueprint("Tolerancia vencida",
                            "pasaron 20 minutos de la hora reservada sin que llegue el comensal",
                            "el anfitrión marca la reserva como no-show",
                            "la mesa se libera y la reserva aparece en el reporte de ausentismo"))),
            new StoryBlueprint("Unirse a la lista de espera", "comensal",
                    "anotarme en la lista de espera cuando el turno está lleno",
                    "conseguir mesa si alguien cancela",
                    Priority.MEDIUM, 5, StoryStatus.DRAFT, List.of(
                    new CriterionBlueprint("Alta en la lista",
                            "el turno del sábado está lleno",
                            "el comensal deja su nombre y celular en la lista de espera",
                            "queda en la cola y ve su posición"),
                    new CriterionBlueprint("Mesa liberada",
                            "se cancela una reserva del turno",
                            "se libera la mesa",
                            "se avisa por WhatsApp al primero de la lista, que tiene 15 minutos para "
                                    + "confirmar")))
    );

    private DemoDiscoveryContent() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Builds the demo discovery content of {@code projectId}: a {@code COMPLETED} session, its final
     * transcript segments, the stories extracted from it and two {@code PENDING} suggestions. Nothing is
     * persisted.
     */
    public static Content build(UUID projectId) {
        DiscoverySession session = new DiscoverySession(projectId, SESSION_TITLE, LANGUAGE);
        session.uploadTranscript(transcriptText(), TRANSCRIPT.size() * LINE_DURATION_MS);
        session.startProcessing();
        session.complete();

        return new Content(
                session,
                segments(session.getId()),
                stories(session.getId(), projectId),
                suggestions(session.getId(), projectId));
    }

    /** Number of sample stories, criteria and suggestions — handy for checks after a restore. */
    public static int storyCount() {
        return STORIES.size();
    }

    public static int segmentCount() {
        return TRANSCRIPT.size();
    }

    /** The aggregates making up the demo discovery content, in the order they must be saved. */
    public record Content(
            DiscoverySession session,
            List<TranscriptSegment> segments,
            List<UserStory> stories,
            List<Suggestion> suggestions
    ) {}

    private static String transcriptText() {
        return TRANSCRIPT.stream().map(Line::text).collect(Collectors.joining("\n"));
    }

    private static List<TranscriptSegment> segments(UUID sessionId) {
        List<TranscriptSegment> segments = new ArrayList<>();
        for (int i = 0; i < TRANSCRIPT.size(); i++) {
            Line line = TRANSCRIPT.get(i);
            long start = i * LINE_DURATION_MS;
            segments.add(new TranscriptSegment(sessionId, i + 1, line.speaker(), line.text(),
                    start, start + LINE_DURATION_MS - 1_000, true));
        }
        return segments;
    }

    private static List<UserStory> stories(UUID sessionId, UUID projectId) {
        List<UserStory> stories = new ArrayList<>();
        for (StoryBlueprint blueprint : STORIES) {
            UserStory story = new UserStory(sessionId, projectId, blueprint.title(), blueprint.role(),
                    blueprint.action(), blueprint.benefit(), blueprint.priority(), blueprint.storyPoints());
            blueprint.criteria().forEach(c ->
                    story.addAcceptanceCriterion(c.scenario(), c.given(), c.when(), c.then()));
            story.changeReviewStatus(blueprint.status());
            stories.add(story);
        }
        return stories;
    }

    private static List<Suggestion> suggestions(UUID sessionId, UUID projectId) {
        Suggestion consent = Suggestion.newStory(sessionId, projectId,
                "Pedir solo los datos necesarios y el consentimiento",
                "comensal",
                "dar solo mi nombre, celular y correo y aceptar expresamente que me contacten",
                "mantener el control sobre mis datos personales",
                Priority.MEDIUM, 2,
                List.of(new Suggestion.DraftCriterion("Sin consentimiento",
                        "el comensal completa el formulario de reserva",
                        "no marca la casilla de consentimiento",
                        "no puede enviar la reserva y se le explica por qué se necesita")));
        Suggestion deposit = Suggestion.clarifyingQuestion(sessionId, projectId,
                "¿Las reservas de fin de semana deberían pedir un adelanto o una garantía con tarjeta para "
                        + "reducir los no-show?");
        return List.of(consent, deposit);
    }

    private record Line(String speaker, String text) {}

    private record StoryBlueprint(String title, String role, String action, String benefit,
                                  Priority priority, @Nullable Integer storyPoints, StoryStatus status,
                                  List<CriterionBlueprint> criteria) {}

    private record CriterionBlueprint(@Nullable String scenario, String given, String when, String then) {}
}
