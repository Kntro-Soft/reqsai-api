package com.kntro.reqsai.discovery.infrastructure.ai.generation.strategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import tools.jackson.databind.ObjectMapper;
import com.kntro.reqsai.discovery.application.port.GenerationContext;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.TokenUsageRecorderPort;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import com.kntro.reqsai.discovery.domain.model.Priority;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Shared base for LLM-backed {@link RequirementGenerationPort} adapters.
 * Contains the extraction prompt, JSON parsing, Markdown stripping, context injection,
 * and null-safe model invocation. Subclasses implement {@link #callModel(String)} and
 * {@link #modelName()} to wire a specific ChatModel.
 */
@Slf4j
abstract class AbstractLlmGenerationAdapter implements RequirementGenerationPort {

    static final String EXTRACTION_PROMPT = """
            You are an expert requirements analyst specializing in agile software development.
            Analyze the following requirements meeting transcript and extract user stories.

            Rules:
            - Group related mentions into a single story (avoid duplicates).
            - Use the SAME LANGUAGE as the transcript for all text fields.
            - LANGUAGE CONSISTENCY: if a fragment is in a clearly different language than the rest of the
              transcript it is almost certainly a mistranscription — omit it, do not build a story around
              it. Every story must be written in the transcript's language.
            - AMBIGUITY → ASK, DO NOT GUESS (STRICT): a user story is valid only when TESTABLE — concrete
              actor, concrete action, verifiable outcome. You MUST output a CLARIFYING_QUESTION (in the
              "questions" array, NOT a NEW_STORY) whenever the requirement (a) uses hand-wavy / defer-to-you
              language with no testable spec ("flexible", "rápido", "seguro", "moderno", "que se adapte a
              lo que venga", "ustedes ya saben", "lo que corresponda"), (b) is missing a decision needed to
              build it (no actor, no threshold/amount, no format/fields/channel, no roles), or (c) states a
              CONFLICT (two mutually exclusive requirements, e.g. "automático y manual a la vez", "gratis
              pero cobrar el flete"). Name the specific missing detail in the question. Do NOT invent a
              plausible value and emit a NEW_STORY — asking is correct, guessing is a defect. A concrete
              requirement (e.g. "iniciar sesión con correo y contraseña") is testable — emit the story.
            - DISTINCT CAPABILITIES STAY SEPARATE (STRICT): when the transcript mentions two or more
              genuinely DIFFERENT capabilities, emit a SEPARATE story for EACH. When the speaker EXPLICITLY
              SIGNALS separation — "y aparte", "por otro lado", "por separado", "distinto", "diferente",
              "otra cosa", "algo separado"; "and separately", "on the other hand", "a different thing" —
              you MUST emit one story per capability and NEVER collapse them into a single conjunction
              title. E.g. "iniciar sesión … y aparte, distinto, restablecer la contraseña" → TWO stories
              ("Iniciar sesión" + "Restablecer contraseña"), NOT one "Iniciar sesión y restablecer
              contraseña"; "ver la lista de pedidos … y aparte, distinto, abrir el detalle de un pedido" →
              TWO stories, NOT "Ver lista y detalle de pedidos". Do NOT over-split a SINGLE capability that
              merely has two delivery channels (e.g. notificaciones por correo Y push is ONE story).
            - CRITICAL: Return ONLY valid JSON — no markdown, no code fences, no explanation.

            Classify each item with a "type":
            - "NEW_STORY"   — a new, standalone user story.
            - "EDGE_CASE"   — a boundary or exceptional scenario that belongs as an acceptance criterion
                              on an existing story (not a new story); include a "relatedTopic" hint and
                              put the boundary rule as EXACTLY ONE Given/When/Then entry in
                              "acceptanceCriteria".
            - "CLARIFYING_QUESTION" — the transcript is ambiguous; ask a question instead of guessing.
                                      Use the "questions" array, NOT the "stories" array.

            For EACH acceptance criterion give a concise "scenario" label (max 200 chars) in the
            transcript language; use null only if you truly cannot.

            Priority mapping (based on context and language cues):
            - CRITICAL: explicit musts, "debe", "necesita", "es imprescindible", "must", "need", "required"
            - HIGH: important needs, "quiere", "importante", "should", "want"
            - MEDIUM: desirable features, "podría", "sería bueno", "could", "nice to have"
            - LOW: implied or mentioned in passing

            Story points (based on apparent complexity):
            - 1: trivial (hours)  |  2: simple (1 day)  |  3: moderate (2-3 days)
            - 5: complex (1 week) |  8: very complex (2+ weeks) | 13: epic (must be split)

            Return ONLY this JSON structure:
            {
              "stories": [
                {
                  "type": "NEW_STORY | EDGE_CASE",
                  "title": "Short descriptive title (max 200 chars)",
                  "role": "User role / actor (max 500 chars)",
                  "action": "What they want to do (max 500 chars)",
                  "benefit": "Expected benefit or reason (max 500 chars)",
                  "priority": "CRITICAL | HIGH | MEDIUM | LOW",
                  "storyPoints": 1,
                  "relatedTopic": "Only for EDGE_CASE: brief topic hint (max 200 chars) or null",
                  "acceptanceCriteria": [
                    {
                      "scenario": "Brief label for this criterion in the transcript language (max 200 chars); null only if impossible",
                      "given": "Given context / precondition (max 1000 chars)",
                      "when": "When this action is performed (max 1000 chars)",
                      "then": "Then this outcome should occur (max 1000 chars)"
                    }
                  ]
                }
              ],
              "questions": [
                { "question": "Clarifying question text (max 1000 chars)" }
              ]
            }

            Transcript:
            %s
            """;

    private static final String CONTEXTUAL_EXTRACTION_PROMPT = """
            You are an expert requirements analyst specializing in agile software development.
            Use the PROJECT CONTEXT below to understand the domain and generate accurate suggestions.

            %s

            Rules:
            - Group related mentions into a single story (avoid duplicates).
            - Apply domain glossary terms where they match the conversation.
            - OUTPUT LANGUAGE: write every text field (title, role, action, benefit, criteria,
              scenario labels, questions) in the SESSION LANGUAGE, given below as "Output language".
              This is the language of the project/session, which may differ from the language the
              speaker happened to use. If the transcript (or a fragment) is in a DIFFERENT language
              than the session language, FULLY translate the INTENT into the session language — never
              emit a story in a language other than the session language, and never mix languages within
              a story. This applies EVEN to closely-related languages: a Portuguese transcript in a
              Spanish session must produce SPANISH, not Portuguese-looking text — e.g. translate
              "rastrear o estado do envio" → "rastrear el estado del envío" (not "o estado do envio"),
              "contracheque" → "boleta de pago", "férias" → "vacaciones". Tech loanwords Spanish uses
              as-is (email, login, push, online, web, app) may stay. (When the transcript is already in
              the session language, this is a no-op.)
            - CRITICAL — EXISTING BACKLOG (candidate matches): the EXISTING USER STORIES list below is a
              set of candidate existing stories retrieved as most similar to this conversation. Check it
              BEFORE emitting anything. If the transcript describes the SAME capability as one of these —
              EVEN IN DIFFERENT WORDS, SYNONYMS, A REGIONAL VARIANT, OR ANOTHER LANGUAGE (e.g. "exportar
              reportes a PDF" ≡ "descargar informes en PDF"; "pagar el carrito" ≡ "cancelar/abonar la
              cesta"; "iniciar sesión" ≡ "autenticarse con credenciales") — do NOT create a NEW_STORY:
              emit UPDATE_STORY with "targetStoryId" set to that existing story's id. If the transcript
              ADDS a new detail, criterion, constraint, or refinement to a capability already in the
              list, ALSO emit UPDATE_STORY (or EDGE_CASE for a boundary rule) targeting that story.
              Emit NEW_STORY ONLY for a genuinely new capability that none of the listed stories covers.
              Examples:
                · Backlog has "<id> | Exportar reportes a PDF"; transcript says "necesito descargar mis
                  informes en formato PDF para remitirlos al equipo" → UPDATE_STORY, targetStoryId=<id>
                  (same capability, only synonyms differ — NOT a new story).
                · Backlog has "<id> | Iniciar sesión"; transcript says "sobre el login: además quiero 2FA"
                  → UPDATE_STORY (or EDGE_CASE) targetStoryId=<id> (adds a detail to an existing story).
              Verbal cues that almost always mean UPDATE_STORY of an existing story (bilingual):
              "volviendo a…", "sobre lo de…", "sobre el/la … que ya tenemos", "además … debe…",
              "también quiero que … soporte…", "cambiar…", "en realidad…"; "going back to…",
              "also it should…", "actually…", "on top of that…", "let's change…". Match them to the
              story they refer to by meaning.
            - QUALITY BAR: if a transcript fragment is garbled, truncated, contradictory or you
              cannot form a coherent, complete user story from it, do NOT emit a suggestion. Speech
              recognition mishears words (e.g. "inicio de sesión" → "inicio de decisión"); never
              invent a requirement around an obvious mistranscription. Prefer emitting nothing over a
              nonsensical story.
            - IGNORE GARBAGE: if the transcript is pure noise — random/invented tokens, gibberish,
              filler-only ("eh, este, o sea, ajá, mmm"), or bare numbers/codes/IDs (e.g.
              "12345 ID-9981 REF-0042 SKU-77") with no real requirement — produce NOTHING: return
              empty "stories" AND empty "questions". Do not ask a clarifying question about noise. If
              real content merely CONTAINS some noise, extract the real capability and ignore the noise.
            - AMBIGUITY → ASK, DO NOT GUESS (STRICT — this is a hard rule, the model tends to guess): a
              user story is only valid when it is TESTABLE — a concrete actor, a concrete action, and a
              verifiable outcome. Before writing a NEW_STORY, check the requirement against this bar. You
              MUST output a CLARIFYING_QUESTION (in the "questions" array, NOT a NEW_STORY) whenever the
              requirement:
                (a) uses hand-wavy / defer-to-you language with no testable spec — "flexible", "rápido",
                    "seguro", "moderno", "ágil", "que se adapte a lo que venga", "lo relevante", "ustedes
                    ya saben", "ya me entienden", "lo que corresponda", "vean ustedes", "eso lo definimos
                    luego"; or
                (b) is missing a decision needed to build it — no actor ("alguien debe aprobar"), no
                    threshold/amount ("con qué monto"), no format/fields/channel ("no sabemos si correo,
                    SMS o push"), no roles ("qué roles existen"); or
                (c) states a CONFLICT — two mutually exclusive requirements ("automático y manual a la
                    vez", "gratis pero también cobrar el flete", "que se confirme automáticamente pero
                    también que alguien la revise").
              The clarifying question MUST NAME the specific missing detail. Do NOT invent a plausible
              value and emit a NEW_STORY instead — asking is correct, guessing is a defect. This applies
              even when there IS a related backlog candidate: if the new ask is itself vague, ask.
              Examples:
                · "Necesitamos que el módulo de pagos sea flexible; ustedes ya saben cómo" → NOT a story.
                  questions: [{"question":"¿Qué métodos de pago debe soportar el módulo (tarjeta, Yape,
                  transferencia) y qué significa 'flexible' en términos concretos?"}]
                · "Alguien debe poder aprobar los gastos, pero no definimos quién ni con qué monto" → NOT
                  a story. questions: [{"question":"¿Qué rol aprueba los gastos y a partir de qué monto se
                  requiere aprobación?"}]
              Counter-example (do NOT over-clarify): a concrete requirement like "el usuario inicia sesión
              con correo y contraseña" is testable — emit the NEW_STORY, do not ask.
            - DISTINCT CAPABILITIES STAY SEPARATE (STRICT): when the transcript mentions two or more
              genuinely DIFFERENT capabilities (e.g. "exportar a PDF" AND "exportar a Excel"; "iniciar
              sesión" AND "registrarse"), emit a SEPARATE story for EACH. Never merge distinct capabilities
              into one story just because they share a topic or a verb.
              When the speaker EXPLICITLY SIGNALS separation — "y aparte", "por otro lado", "por separado",
              "distinto", "diferente", "otra cosa", "algo separado", "además de eso, algo distinto";
              "and separately", "on the other hand", "a different thing", "besides that, something
              separate" — you MUST emit one story per capability. NEVER collapse them into a single
              conjunction title. Counter-examples of the WRONG merge you must avoid:
                · "iniciar sesión con correo y contraseña. Y aparte, distinto, restablecer la contraseña
                  por un enlace" → TWO stories ("Iniciar sesión" + "Restablecer contraseña"), NOT one
                  "Iniciar sesión y restablecer contraseña".
                · "ver la lista de todos mis pedidos. Y aparte, distinto, abrir el detalle de un pedido"
                  → TWO stories ("Ver lista de pedidos" + "Ver detalle de un pedido"), NOT one
                  "Ver lista y detalle de pedidos".
              This is about SEPARATION language + genuinely different capabilities. Do NOT over-split a
              SINGLE capability that merely has two delivery channels or options (e.g. "recibir
              notificaciones por correo Y push" is ONE notification capability with two channels — keep it
              as one story with a criterion per channel).
            - GRANULARITY: session maintenance (keeping a user logged in), error/validation messages,
              input validations, and security constraints (encryption, rate limits, password policy)
              OF an existing capability are NOT separate stories. Emit them as EDGE_CASE (acceptance
              criterion) or UPDATE_STORY on the capability they belong to, never as a standalone
              NEW_STORY. Examples:
                · "mantener la sesión activa" → EDGE_CASE / UPDATE_STORY of the login story, not new.
                · "mostrar un mensaje de error si la contraseña es inválida" → EDGE_CASE of login.
                · "the export must be encrypted" → EDGE_CASE / UPDATE_STORY of the export story.
            - Do NOT re-suggest anything equivalent (same meaning, any wording or language) to an item
              in ALREADY SUGGESTED THIS SESSION; those are pending analyst review and repeating them
              floods the queue. This is a hard constraint, not a preference.
            - For every NEW_STORY, propose 2 to 4 acceptance criteria, each an explicit
              Given / When / Then triple in the SAME LANGUAGE as the transcript. Base them on what was
              actually said; do not fabricate. If you cannot form at least one complete Given/When/Then
              triple, return an empty "acceptanceCriteria" array rather than inventing one.
            - For every EDGE_CASE, provide EXACTLY ONE acceptance criterion in "acceptanceCriteria":
              the boundary/exceptional/validation/security rule itself, as an explicit
              Given / When / Then triple, plus the existing story it belongs to in "targetStoryId".
              Do NOT restate the parent story as an edge case.
            - For EACH acceptance criterion (NEW_STORY list and the single EDGE_CASE one), also give a
              concise "scenario" label (max 200 chars) in the SAME LANGUAGE as the transcript. Omit it
              (null) only if you truly cannot; never fabricate one.
            - CRITICAL: Return ONLY valid JSON — no markdown, no code fences, no explanation.

            Classify each item with a "type":
            - "NEW_STORY"    — a new, standalone user story not covered by any existing story in the context.
                               "targetStoryId" must be null.
            - "UPDATE_STORY" — the conversation revisits, refines, extends, changes or duplicates an
                               EXISTING user story from the list; set "targetStoryId" to that story's id
                               and write the full updated story fields.
            - "EDGE_CASE"    — a boundary, exceptional scenario, or a session-maintenance / error /
                               validation / security constraint that belongs as an acceptance criterion
                               on an existing story rather than as a new standalone story; set
                               "targetStoryId" to that story's id when you can identify it, include a
                               "relatedTopic" hint (a glossary term or a concept already mentioned in the
                               context), and put the boundary rule itself as EXACTLY ONE Given/When/Then
                               entry in "acceptanceCriteria".
            - "CLARIFYING_QUESTION" — the transcript is ambiguous; ask a question instead of guessing.
                                      Use the "questions" array, NOT the "stories" array.

            Priority mapping (based on context and language cues):
            - CRITICAL: explicit musts, "debe", "necesita", "es imprescindible", "must", "need", "required"
            - HIGH: important needs, "quiere", "importante", "should", "want"
            - MEDIUM: desirable features, "podría", "sería bueno", "could", "nice to have"
            - LOW: implied or mentioned in passing

            Story points (based on apparent complexity):
            - 1: trivial (hours)  |  2: simple (1 day)  |  3: moderate (2-3 days)
            - 5: complex (1 week) |  8: very complex (2+ weeks) | 13: epic (must be split)

            Return ONLY this JSON structure:
            {
              "stories": [
                {
                  "type": "NEW_STORY | UPDATE_STORY | EDGE_CASE",
                  "targetStoryId": "id of the existing story for UPDATE_STORY / EDGE_CASE, or null",
                  "title": "Short descriptive title (max 200 chars)",
                  "role": "User role / actor (max 500 chars)",
                  "action": "What they want to do (max 500 chars)",
                  "benefit": "Expected benefit or reason (max 500 chars)",
                  "priority": "CRITICAL | HIGH | MEDIUM | LOW",
                  "storyPoints": 1,
                  "relatedTopic": "Only for EDGE_CASE: glossary term or concept the edge case belongs to, or null",
                  "acceptanceCriteria": [
                    {
                      "scenario": "Brief label for this criterion in the transcript language (max 200 chars); null only if impossible",
                      "given": "Given context / precondition (max 1000 chars)",
                      "when": "When this action is performed (max 1000 chars)",
                      "then": "Then this outcome should occur (max 1000 chars)"
                    }
                  ]
                }
              ],
              "questions": [
                { "question": "Clarifying question text (max 1000 chars)" }
              ]
            }

            NEW_STORY: 2-4 acceptance criteria. EDGE_CASE: exactly one (the boundary rule).

            %s

            DEDUP DECISION (do this FIRST, before writing any story): for EACH capability in the RECENT
            CONVERSATION below, scan the CANDIDATE EXISTING STORIES list directly above.
            - If the capability is the SAME as a candidate — even in different words, synonyms, a regional
              variant, or another language — you MUST output "type":"UPDATE_STORY" with "targetStoryId"
              set to that candidate's id, COPIED VERBATIM from the list. Do NOT output NEW_STORY for it.
            - If it ADDS a detail/criterion/constraint to a candidate, you MUST output "UPDATE_STORY" (or
              "EDGE_CASE" for a boundary rule) with "targetStoryId" set to that candidate's id.
            - Output "NEW_STORY" (with "targetStoryId": null) ONLY when NO candidate matches.
            Worked example — if CANDIDATE EXISTING STORIES contains
              "11111111-1111-1111-1111-111111111111 | Exportar reportes a PDF"
            and the conversation says "necesito descargar mis informes en formato PDF para el equipo",
            you MUST return:
              {"stories":[{"type":"UPDATE_STORY","targetStoryId":"11111111-1111-1111-1111-111111111111",
                "title":"Exportar reportes a PDF","role":"usuario","action":"descargar mis informes en PDF",
                "benefit":"compartirlos con el equipo","priority":"HIGH","storyPoints":3,
                "acceptanceCriteria":[]}],"questions":[]}
            (targetStoryId is the candidate id echoed verbatim — that is the whole point.)

            Recent conversation:
            %s
            """;

    private final ObjectMapper objectMapper;
    private final TokenUsageRecorderPort tokenUsageRecorder;

    protected AbstractLlmGenerationAdapter(ObjectMapper objectMapper) {
        this(objectMapper, tokens -> { /* no-op: metering disabled (e.g. in unit tests) */ });
    }

    protected AbstractLlmGenerationAdapter(ObjectMapper objectMapper, TokenUsageRecorderPort tokenUsageRecorder) {
        this.objectMapper = objectMapper;
        this.tokenUsageRecorder = tokenUsageRecorder;
    }

    /** Sends {@code promptText} to the concrete model and returns the raw text response. */
    protected abstract String callModel(String promptText);

    /** Name used in log messages and error details (e.g. "Gemini", "OpenAI"). */
    protected abstract String modelName();

    @Override
    public GenerationResult generate(String transcript, String language) {
        log.debug("Sending extraction prompt to {} ({} chars)", modelName(), transcript.length());
        return callAndParse(EXTRACTION_PROMPT.formatted(transcript));
    }

    @Override
    public GenerationResult generate(String transcript, String language, @Nullable GenerationContext context) {
        if (context == null) return generate(transcript, language);
        String contextBlock = buildContextBlock(context, language);
        // Render the ID-bearing candidate list AGAIN, immediately before the transcript/task, so the ids
        // sit next to the DEDUP DECISION step instead of ~130 lines up in the project context (where the
        // model reliably ignored them: 98/98 targetStoryId=null). Placement + an imperative "you MUST
        // copy the id" is the fix — the field and the ids were already present but too far from the task.
        String candidatesBlock = buildCandidatesBlock(context);
        log.debug("Sending contextual extraction prompt to {} ({} chars)", modelName(), transcript.length());
        return callAndParse(CONTEXTUAL_EXTRACTION_PROMPT.formatted(contextBlock, candidatesBlock, transcript));
    }

    private GenerationResult callAndParse(String promptText) {
        // TRACE-gated prompt/response dump — off by default; flip Discovery generation logging to TRACE to
        // verify end-to-end that candidate ids reach the model and whether it echoes a targetStoryId.
        if (log.isTraceEnabled()) {
            log.trace("=== PROMPT SENT TO {} ===\n{}\n=== END PROMPT ===", modelName(), promptText);
        }
        String json = stripMarkdown(callModel(promptText));
        if (log.isTraceEnabled()) {
            log.trace("=== RAW {} RESPONSE ===\n{}\n=== END RESPONSE ===", modelName(), json);
        }
        log.debug("{} response ({} chars)", modelName(), json.length());
        return parseJsonResponse(json);
    }

    private static String buildContextBlock(GenerationContext ctx, @Nullable String outputLanguage) {
        StringBuilder sb = new StringBuilder();
        if (outputLanguage != null && !outputLanguage.isBlank()) {
            sb.append("Output language (write EVERY text field in this language, translating the intent")
              .append(" when the transcript uses another language): ").append(outputLanguage).append("\n");
        }
        sb.append("PROJECT: ").append(ctx.projectName()).append("\n");
        if (ctx.projectDescription() != null) {
            sb.append("Description: ").append(ctx.projectDescription()).append("\n");
        }
        if (!ctx.programmingLanguages().isEmpty() || !ctx.frameworks().isEmpty()) {
            sb.append("Tech stack: ");
            if (!ctx.programmingLanguages().isEmpty()) sb.append(String.join(", ", ctx.programmingLanguages()));
            if (!ctx.frameworks().isEmpty()) sb.append(" | ").append(String.join(", ", ctx.frameworks()));
            if (!ctx.databases().isEmpty()) sb.append(" | DB: ").append(String.join(", ", ctx.databases()));
            sb.append("\n");
        }
        if (ctx.architecture() != null && !ctx.architecture().isBlank()) {
            sb.append("Architecture: ").append(ctx.architecture()).append("\n");
        }
        if (ctx.domain() != null && !ctx.domain().isBlank()) {
            sb.append("Domain: ").append(ctx.domain()).append("\n");
        }
        if (!ctx.constraints().isEmpty()) {
            sb.append("Constraints:\n");
            ctx.constraints().forEach(c -> sb.append("- ").append(c).append("\n"));
        }
        if (!ctx.glossaryTerms().isEmpty()) {
            sb.append("Domain glossary:\n");
            ctx.glossaryTerms().forEach(g -> sb.append("- ").append(g.term()).append(": ").append(g.definition()).append("\n"));
        }
        sb.append("\nEXISTING USER STORIES (candidate matches from the current backlog, most similar first;")
          .append(" format: id | title | as <role> I want <action> so that <benefit>). If the transcript")
          .append(" describes the SAME capability as one of these — even in different words — emit")
          .append(" UPDATE_STORY targeting its id, do NOT create a NEW_STORY:\n");
        if (ctx.existingStories().isEmpty()) {
            sb.append("- none yet\n");
        } else {
            ctx.existingStories().forEach(s -> sb.append("- ").append(s.id())
                    .append(" | ").append(s.title())
                    .append(" | as ").append(s.role())
                    .append(" I want ").append(s.action())
                    .append(" so that ").append(s.benefit())
                    .append("\n"));
        }
        if (!ctx.alreadySuggested().isEmpty()) {
            sb.append("\nALREADY SUGGESTED THIS SESSION (format: id | summary) — pending analyst review. Do")
              .append(" NOT emit anything equivalent to these (same meaning in any wording or language); they")
              .append(" are already in the queue. If the conversation REFINES or EXTENDS one of these pending")
              .append(" items, emit UPDATE_STORY (or EDGE_CASE) with that item's id as \"targetStoryId\"")
              .append(" instead of a near-duplicate NEW_STORY:\n");
            ctx.alreadySuggested().forEach(p -> sb.append("- ").append(p.id())
                    .append(" | ").append(p.summary()).append("\n"));
        }
        return sb.toString().strip();
    }

    /**
     * An explicit, compact, ID-bearing candidate list rendered immediately before the transcript so the
     * ids are adjacent to the DEDUP DECISION instruction. Each line is {@code <uuid> | <title>} so the
     * model can copy the id verbatim into {@code targetStoryId}. Backlog stories AND still-pending
     * suggestions are both listed (both are legal {@code targetStoryId} targets). Titles are truncated
     * to keep the block small on a large backlog.
     */
    private static String buildCandidatesBlock(GenerationContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("CANDIDATE EXISTING STORIES (copy an id verbatim into \"targetStoryId\" when the")
          .append(" conversation matches or extends one; format: <id> | <title>):\n");
        if (ctx.existingStories().isEmpty() && ctx.alreadySuggested().isEmpty()) {
            sb.append("- (none — the backlog is empty; every capability is a NEW_STORY)\n");
            return sb.toString().strip();
        }
        ctx.existingStories().forEach(s ->
                sb.append("- ").append(s.id()).append(" | ").append(truncate(s.title())).append("\n"));
        ctx.alreadySuggested().forEach(p ->
                sb.append("- ").append(p.id()).append(" | ").append(truncate(p.summary()))
                  .append(" (pending)\n"));
        return sb.toString().strip();
    }

    /** Caps a candidate title/summary so the CANDIDATES block stays small on a large backlog. */
    private static String truncate(@Nullable String value) {
        if (value == null) return "";
        String v = value.strip();
        return v.length() <= 120 ? v : v.substring(0, 117) + "...";
    }

    protected String callAndExtractText(ChatModel model, String promptText) {
        ChatResponse response = model.call(new Prompt(promptText));
        recordTokenUsage(response);
        var result = response != null ? response.getResult() : null;
        String text = result != null ? result.getOutput().getText() : null;
        if (text == null || text.isBlank()) {
            throw DiscoveryInfrastructureExceptions.generationFailed("Empty response from AI model");
        }
        return text;
    }

    /**
     * Best-effort AI-token metering: reads the provider-reported total token usage from the response
     * metadata and reports it to Billing. Never throws — a metering failure must not fail generation.
     */
    private void recordTokenUsage(@Nullable ChatResponse response) {
        try {
            if (response == null || response.getMetadata() == null) {
                return;
            }
            var usage = response.getMetadata().getUsage();
            if (usage == null) {
                return;
            }
            Number total = usage.getTotalTokens();
            if (total != null && total.longValue() > 0) {
                tokenUsageRecorder.record(total.longValue());
            }
        } catch (RuntimeException e) {
            log.debug("Token usage metering skipped for {}: {}", modelName(), e.getMessage());
        }
    }

    /** Strips optional Markdown code fences that some models wrap around JSON. */
    protected static String stripMarkdown(String text) {
        String s = text.strip();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.strip();
    }

    protected GenerationResult parseJsonResponse(String json) {
        try {
            if (json.startsWith("[")) {
                log.debug("{} returned array instead of object — treating as no stories", modelName());
                return new GenerationResult(List.of(), List.of());
            }
            LlmResponse parsed = objectMapper.readValue(json, LlmResponse.class);

            List<GenerationResult.GeneratedStory> stories = parsed.stories() == null
                    ? List.of()
                    : parsed.stories().stream().map(this::toGeneratedStory).toList();

            List<GenerationResult.GeneratedQuestion> questions = parsed.questions() == null
                    ? List.of()
                    : parsed.questions().stream()
                    .filter(q -> q.question() != null && !q.question().isBlank())
                    .map(q -> new GenerationResult.GeneratedQuestion(q.question()))
                    .toList();

            return new GenerationResult(stories, questions);
        } catch (Exception e) {
            log.error("Failed to parse {} response: {}", modelName(), e.getMessage());
            log.debug("Full {} response was: {}", modelName(), json);
            throw DiscoveryInfrastructureExceptions.generationFailed("Invalid JSON from " + modelName() + ": " + e.getMessage(), e);
        }
    }

    private GenerationResult.GeneratedStory toGeneratedStory(LlmStory story) {
        List<GenerationResult.GeneratedCriterion> criteria = story.acceptanceCriteria() == null
                ? List.of()
                : story.acceptanceCriteria().stream().map(this::toGeneratedCriterion).toList();

        SuggestionType type = parseSuggestionType(story.type());

        return new GenerationResult.GeneratedStory(
                type,
                story.title(), story.role(), story.action(), story.benefit(),
                parsePriority(story.priority()), story.storyPoints(),
                criteria, story.relatedTopic(), parseUuid(story.targetStoryId()));
    }

    private GenerationResult.GeneratedCriterion toGeneratedCriterion(LlmCriterion criterion) {
        return new GenerationResult.GeneratedCriterion(criterion.scenario(), criterion.given(), criterion.when(), criterion.then());
    }

    protected static Priority parsePriority(String value) {
        if (value == null) return Priority.MEDIUM;
        try {
            return Priority.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Priority.MEDIUM;
        }
    }

    protected static SuggestionType parseSuggestionType(@Nullable String value) {
        if (value == null) return SuggestionType.NEW_STORY;
        try {
            return SuggestionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SuggestionType.NEW_STORY;
        }
    }

    /** Parses a UUID the LLM echoed back, tolerating null/blank/hallucinated values. */
    protected static java.util.@Nullable UUID parseUuid(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return java.util.UUID.fromString(value.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Shared Jackson records for all LLM adapters

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmResponse(List<LlmStory> stories, @Nullable List<LlmQuestion> questions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmStory(@Nullable String type, String title, String role, String action, String benefit,
                               String priority, @Nullable Integer storyPoints,
                               @Nullable String relatedTopic, @Nullable String targetStoryId,
                               @Nullable List<LlmCriterion> acceptanceCriteria) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmCriterion(@Nullable String scenario, String given, String when, String then) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record LlmQuestion(String question) {}
}
