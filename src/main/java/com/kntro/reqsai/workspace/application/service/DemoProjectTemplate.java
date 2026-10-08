package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Static sample content of the demo project every new organization receives (US28): a Peruvian restaurant
 * that wants online table reservations. Covers the workspace-owned part — project profile, glossary and
 * constraints; Discovery seeds the session, stories and suggestions on its side.
 * <p>
 * Pure data plus a few builders that go through the aggregates' own behavior ({@link Project#createDemo},
 * {@link Project#addConstraint}, {@link Glossary#addTerm}), so the demo obeys the same invariants as
 * user-created content. No AI involved: the content is fixed and stays un-embedded until the lazy
 * re-index paths pick it up.
 */
public final class DemoProjectTemplate {

    public static final String NAME = "Demo · Restaurante La Tradición — Reservas en línea";

    public static final String DESCRIPTION = "Proyecto de demostración: una plataforma web y móvil para que los "
            + "comensales de La Tradición, un restaurante criollo de Lima, reserven mesa en línea y reciban su "
            + "confirmación al instante, y para que el personal gestione la ocupación del salón en cada turno. "
            + "Explóralo libremente: puedes restaurar los datos de prueba cuando quieras.";

    private static final List<GlossaryEntry> GLOSSARY = List.of(
            new GlossaryEntry("Comensal",
                    "Cliente final que reserva una mesa y asiste al restaurante."),
            new GlossaryEntry("Turno",
                    "Franja horaria de servicio en la que se pueden reservar mesas: almuerzo de 12:00 a 16:00 "
                            + "y cena de 19:00 a 23:00."),
            new GlossaryEntry("Aforo",
                    "Número máximo de comensales que el salón puede atender a la vez durante un turno."),
            new GlossaryEntry("No-show",
                    "Reserva confirmada a la que el comensal no asiste ni cancela con anticipación."),
            new GlossaryEntry("Lista de espera",
                    "Cola de comensales que piden mesa cuando el turno está lleno y reciben un aviso si se "
                            + "libera un lugar.")
    );

    private static final List<String> CONSTRAINTS = List.of(
            "Los datos personales de los comensales deben tratarse conforme a la Ley N.° 29733 de Protección "
                    + "de Datos Personales del Perú.",
            "La confirmación de una reserva debe llegar al comensal en menos de 1 minuto por correo o WhatsApp.",
            "La aplicación debe funcionar en celulares de gama media con conexión 4G inestable."
    );

    private DemoProjectTemplate() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** One sample glossary entry. */
    public record GlossaryEntry(String term, String definition) {}

    /** The sample technical profile (stack, platforms, architecture and business domain). */
    public static TechnicalProfile profile() {
        return new TechnicalProfile(
                List.of("TypeScript", "Java"),
                List.of("Angular", "Spring Boot"),
                List.of("Web", "Android", "iOS"),
                List.of("PostgreSQL"),
                "Monolito modular",
                "Gastronomía y reservas"
        );
    }

    public static List<GlossaryEntry> glossary() {
        return GLOSSARY;
    }

    public static List<String> constraints() {
        return CONSTRAINTS;
    }

    /** A new, unsaved demo project for the organization, with its sample profile and constraints. */
    public static Project newProject(UUID organizationId, UUID createdBy) {
        Project project = Project.createDemo(organizationId, NAME, DESCRIPTION, profile(), createdBy);
        project.replaceConstraints(CONSTRAINTS);
        return project;
    }

    /**
     * Puts the project back to its sample state: description, technical profile and constraints. The name
     * is kept so a renamed demo never collides with another project's name.
     */
    public static void resetProject(Project project) {
        project.updateDetails(project.getName(), DESCRIPTION, profile());
        project.replaceConstraints(CONSTRAINTS);
    }

    /** Replaces the glossary's terms with the sample ones; added terms are attributed to {@code addedBy}. */
    public static void resetGlossary(Glossary glossary, UUID addedBy) {
        Map<String, String> definitionsByTerm = new LinkedHashMap<>();
        GLOSSARY.forEach(entry -> definitionsByTerm.put(entry.term(), entry.definition()));
        glossary.replaceTerms(definitionsByTerm, addedBy);
    }
}
