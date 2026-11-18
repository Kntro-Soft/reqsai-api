package com.kntro.reqsai.discovery.infrastructure.quality;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The vocabulary behind the rule-based evaluation, in Spanish and English. Patterns run over text that is
 * already normalized (lower-case, without accents), and only match whole words.
 */
final class QualityLexicon {

    /** A vague term: how it is shown to the analyst and how it is found. */
    record AmbiguousTerm(String display, Pattern pattern) {

        static AmbiguousTerm of(String display, String regex) {
            return new AmbiguousTerm(display, Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + regex + ")(?![\\p{L}\\p{N}])"));
        }
    }

    /**
     * Subjective, comparative, open-ended and loophole terms (ISO/IEC/IEEE 29148 "unambiguous"): they cannot
     * be checked because each reader fills them in differently.
     */
    static final List<AmbiguousTerm> AMBIGUOUS_TERMS = List.of(
            // Spanish
            AmbiguousTerm.of("rápido", "rapid(?:o|a|os|as|amente)"),
            AmbiguousTerm.of("fácil", "facil(?:es|mente)?"),
            AmbiguousTerm.of("sencillo", "sencill(?:o|a|os|as)"),
            AmbiguousTerm.of("simple", "simples?"),
            AmbiguousTerm.of("adecuado", "adecuad(?:o|a|os|as|amente)"),
            AmbiguousTerm.of("apropiado", "apropiad(?:o|a|os|as|amente)"),
            AmbiguousTerm.of("óptimo", "optim(?:o|a|os|as|amente)"),
            AmbiguousTerm.of("eficiente", "eficientes?|eficientemente"),
            AmbiguousTerm.of("eficaz", "eficaz|eficaces"),
            AmbiguousTerm.of("amigable", "amigables?"),
            AmbiguousTerm.of("intuitivo", "intuitiv(?:o|a|os|as)"),
            AmbiguousTerm.of("flexible", "flexibles?"),
            AmbiguousTerm.of("robusto", "robust(?:o|a|os|as)"),
            AmbiguousTerm.of("moderno", "modern(?:o|a|os|as)"),
            AmbiguousTerm.of("cómodo", "comod(?:o|a|os|as)"),
            AmbiguousTerm.of("ágil", "agil|agiles"),
            AmbiguousTerm.of("bueno", "buen(?:o|a|os|as)?"),
            AmbiguousTerm.of("mejor", "mejor|mejores"),
            AmbiguousTerm.of("suficiente", "suficientes?"),
            AmbiguousTerm.of("razonable", "razonables?|razonablemente"),
            AmbiguousTerm.of("correctamente", "correctamente"),
            AmbiguousTerm.of("normalmente", "normalmente|generalmente|habitualmente|usualmente"),
            AmbiguousTerm.of("a veces", "a veces"),
            AmbiguousTerm.of("algunos", "algun(?:o|a|os|as)"),
            AmbiguousTerm.of("varios", "vari(?:o|a|os|as)"),
            AmbiguousTerm.of("muchos", "much(?:o|a|os|as)"),
            AmbiguousTerm.of("pocos", "poc(?:o|a|os|as)"),
            AmbiguousTerm.of("bastante", "bastantes?"),
            AmbiguousTerm.of("lo antes posible", "lo antes posible|tan pronto como sea posible"),
            AmbiguousTerm.of("cuando sea necesario", "cuando sea necesario|si es necesario|segun sea necesario"),
            AmbiguousTerm.of("si es posible", "si es posible|de ser posible|de ser necesario"),
            AmbiguousTerm.of("según corresponda", "segun corresponda|segun aplique"),
            AmbiguousTerm.of("etc.", "etc|etcetera"),
            AmbiguousTerm.of("entre otros", "entre otros|entre otras|y otros|y otras|y demas|y asi sucesivamente"),
            AmbiguousTerm.of("y/o", "y/o"),
            // English
            AmbiguousTerm.of("fast", "fast|faster|quick|quickly|rapid|rapidly"),
            AmbiguousTerm.of("easy", "easy|easily|easier"),
            AmbiguousTerm.of("simple", "simple|simply"),
            AmbiguousTerm.of("user-friendly", "user[- ]friendly|friendly"),
            AmbiguousTerm.of("intuitive", "intuitive|intuitively"),
            AmbiguousTerm.of("flexible", "flexible"),
            AmbiguousTerm.of("robust", "robust"),
            AmbiguousTerm.of("efficient", "efficient|efficiently"),
            AmbiguousTerm.of("optimal", "optimal|optimally"),
            AmbiguousTerm.of("adequate", "adequate|adequately"),
            AmbiguousTerm.of("appropriate", "appropriate|appropriately"),
            AmbiguousTerm.of("modern", "modern"),
            AmbiguousTerm.of("seamless", "seamless|seamlessly"),
            AmbiguousTerm.of("good", "good"),
            AmbiguousTerm.of("better", "better|best"),
            AmbiguousTerm.of("sufficient", "sufficient|enough"),
            AmbiguousTerm.of("reasonable", "reasonable|reasonably"),
            AmbiguousTerm.of("properly", "properly|correctly"),
            AmbiguousTerm.of("usually", "usually|normally|generally|sometimes"),
            AmbiguousTerm.of("some", "some|several|many|few"),
            AmbiguousTerm.of("as soon as possible", "as soon as possible|asap"),
            AmbiguousTerm.of("as needed", "as needed|as necessary|as appropriate|as applicable|if needed"),
            AmbiguousTerm.of("if possible", "if possible|where possible"),
            AmbiguousTerm.of("and so on", "and so on|and more|and others|and the like"),
            AmbiguousTerm.of("and/or", "and/or"));

    /** The ellipsis leaves a list open-ended just like «etc.»; it is not a word so it has its own pattern. */
    static final AmbiguousTerm ELLIPSIS = new AmbiguousTerm("...", Pattern.compile("\\.{3}|…"));

    /** Values that fill a field without saying anything. */
    static final Set<String> PLACEHOLDERS = Set.of(
            "tbd", "tba", "n/a", "na", "xxx", "por definir", "pendiente", "a definir", "sin definir", "todo", "lorem ipsum");

    /** Roles too generic to tell who needs the story. */
    static final Set<String> GENERIC_ROLES = Set.of(
            "usuario", "usuarios", "el usuario", "un usuario", "persona", "alguien", "sistema", "el sistema",
            "user", "users", "the user", "a user", "person", "someone", "system", "the system");

    /** Verbs that start a second action in an action phrase; English has no infinitive ending to rely on. */
    static final Set<String> ENGLISH_VERBS = Set.of(
            "add", "approve", "archive", "assign", "attach", "browse", "cancel", "change", "check", "choose",
            "close", "compare", "configure", "confirm", "copy", "create", "delete", "download", "edit", "enable",
            "enter", "export", "filter", "find", "generate", "get", "import", "invite", "list", "log", "login",
            "manage", "mark", "merge", "monitor", "move", "notify", "open", "pay", "print", "receive", "record",
            "register", "reject", "remove", "rename", "reset", "restore", "review", "save", "schedule", "search",
            "see", "select", "send", "set", "share", "show", "sign", "sort", "submit", "track", "transfer",
            "update", "upload", "validate", "view");

    /** Spanish words ending like an infinitive that are not verbs. */
    static final Set<String> SPANISH_NON_VERBS = Set.of(
            "mar", "bar", "par", "lugar", "hogar", "taller", "cualquier", "primer", "mujer", "ayer", "alquiler");

    /**
     * Verbs that describe something the system does that can be seen or measured: a «Then» that has one (or a
     * number) states an observable outcome. The stems match any ending; the whole words only themselves.
     */
    static final Pattern OBSERVABLE_OUTCOME = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:(?:muestr|mostr|visualiz|aparec|devuelv|devolv|retorn|envi|registr|guard|"
                    + "genera|redirig|bloque|notific|cre[ao]|elimin|actualiz|rechaz|descarg|export|impid|"
                    + "permit|habilit|deshabilit|valid|list|calcul|incluy|contien|respond|obtien|"
                    + "display|show|return|send|sav|creat|delet|updat|reject|redirect|block|notif|download|"
                    + "prevent|allow|enabl|disabl|calculat|contain|includ|receiv|appear)"
                    + "|(?:ve|ver|ven|vea|queda|cambia|pasa|recibe|puede|get|gets|see|sees|can)(?![\\p{L}\\p{N}]))");

    /** A quantity, percentage, time or code: the outcome is measurable. */
    static final Pattern QUANTITY = Pattern.compile("\\d");

    private QualityLexicon() {
    }
}
