package com.kntro.reqsai.codebase.application.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the landmarks of a source file without a full parser: the types and functions it declares, the
 * HTTP endpoints and UI routes it exposes, and the persistent entities / tables it defines. Regexes per
 * language family keep it dependency-free; what they miss the AI still reads in the excerpts.
 */
public final class SymbolExtractor {

    private static final int MAX_PER_FILE = 40;

    /** Declarations: classes, interfaces, records, enums, structs, functions, exported constants. */
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:export\\s+)?(?:default\\s+)?(?:public\\s+|private\\s+|protected\\s+|internal\\s+)?"
                    + "(?:abstract\\s+|final\\s+|sealed\\s+|data\\s+|static\\s+|partial\\s+)*"
                    + "(?:class|interface|record|enum|struct|trait|object|type)\\s+([A-Z][A-Za-z0-9_]{1,60})");
    private static final Pattern FUNCTION_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:export\\s+)?(?:async\\s+)?(?:function|def|func|fun|fn)\\s+(?:\\([^)]{0,80}\\)\\s*)?"
                    + "([A-Za-z_][A-Za-z0-9_]{2,60})\\s*[(<]");
    private static final Pattern EXPORTED_CONST = Pattern.compile(
            "(?m)^\\s*export\\s+const\\s+([A-Za-z_][A-Za-z0-9_]{2,60})\\s*[=:]");
    private static final Pattern JAVA_METHOD = Pattern.compile(
            "(?m)^\\s+(?:public|protected)\\s+(?:static\\s+)?(?:final\\s+)?[A-Za-z0-9_<>,\\[\\]?\\s]{1,60}?\\s+"
                    + "([a-z][A-Za-z0-9_]{2,60})\\s*\\(");

    /** Spring / JAX-RS style mappings: {@code @GetMapping("/x")}, {@code @RequestMapping(path = "/x")}. */
    private static final Pattern SPRING_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Patch|Delete|Request)Mapping\\s*(?:\\(\\s*(?:(?:value|path)\\s*=\\s*)?\\{?\\s*\"([^\"]*)\")?");
    /** Express / Koa / Fastify routers: {@code app.get('/x', …)}, {@code router.post("/x")}. */
    private static final Pattern EXPRESS_ROUTE = Pattern.compile(
            "\\b(?:app|router|server|api|routes?)\\.(get|post|put|patch|delete)\\s*\\(\\s*['\"`]([^'\"`]{1,120})['\"`]");
    /** NestJS / ASP.NET / FastAPI / Flask decorators and attributes. */
    private static final Pattern DECORATOR_ROUTE = Pattern.compile(
            "[@\\[](?:app\\.|router\\.|bp\\.|blueprint\\.)?(Get|Post|Put|Patch|Delete|HttpGet|HttpPost|HttpPut|HttpPatch"
                    + "|HttpDelete|get|post|put|patch|delete|route)\\s*\\(\\s*['\"]([^'\"]{1,120})['\"]");
    /** Laravel and Rails routes. */
    private static final Pattern PHP_RUBY_ROUTE = Pattern.compile(
            "(?:Route::|^\\s*)(get|post|put|patch|delete)\\s*\\(?\\s*['\"]([^'\"]{1,120})['\"]", Pattern.MULTILINE);
    /** Go routers: {@code r.GET("/x", …)}, {@code mux.HandleFunc("/x", …)}. */
    private static final Pattern GO_ROUTE = Pattern.compile(
            "\\.(GET|POST|PUT|PATCH|DELETE|HandleFunc|Handle)\\s*\\(\\s*\"([^\"]{1,120})\"");
    /** Front-end routes: Angular / Vue {@code path: 'x'}, React Router {@code <Route path="x"}. */
    private static final Pattern UI_ROUTE = Pattern.compile(
            "(?:\\bpath\\s*:\\s*|<Route[^>]{0,80}\\bpath\\s*=\\s*)['\"]([^'\"]{1,100})['\"]");

    /** JPA / Hibernate entities and tables. */
    private static final Pattern JPA_ENTITY = Pattern.compile("@Entity\\b[\\s\\S]{0,300}?\\bclass\\s+([A-Z][A-Za-z0-9_]*)");
    private static final Pattern JPA_TABLE = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
    /** SQL migrations. */
    private static final Pattern SQL_TABLE = Pattern.compile(
            "(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?[\"`]?([A-Za-z_][A-Za-z0-9_.]{1,80})");
    /** Mongoose / Sequelize / Django / SQLAlchemy / TypeORM / Prisma models. */
    private static final Pattern ORM_MODEL = Pattern.compile(
            "(?:mongoose\\.model\\s*\\(\\s*['\"]([A-Za-z0-9_]+)|sequelize\\.define\\s*\\(\\s*['\"]([A-Za-z0-9_]+)"
                    + "|class\\s+([A-Z][A-Za-z0-9_]*)\\s*\\(\\s*(?:models\\.Model|Base|db\\.Model)"
                    + "|@Entity\\s*\\(\\s*\\)?\\s*(?:export\\s+)?class\\s+([A-Z][A-Za-z0-9_]*)"
                    + "|^model\\s+([A-Z][A-Za-z0-9_]*)\\s*\\{)", Pattern.MULTILINE);

    private SymbolExtractor() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public record FileSymbols(List<String> symbols, List<String> endpoints, List<String> entities) {
        public static FileSymbols empty() {
            return new FileSymbols(List.of(), List.of(), List.of());
        }
    }

    public static FileSymbols extract(String path, String content) {
        String lower = path.toLowerCase(Locale.ROOT);
        Set<String> symbols = new LinkedHashSet<>();
        Set<String> endpoints = new LinkedHashSet<>();
        Set<String> entities = new LinkedHashSet<>();

        if (lower.endsWith(".sql")) {
            collect(SQL_TABLE, content, 1, entities);
            return result(symbols, endpoints, entities);
        }

        collect(TYPE_DECLARATION, content, 1, symbols);
        collect(FUNCTION_DECLARATION, content, 1, symbols);
        collect(EXPORTED_CONST, content, 1, symbols);
        if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".cs")) {
            collect(JAVA_METHOD, content, 1, symbols);
        }

        String classPrefix = springClassPrefix(content);
        Matcher spring = SPRING_MAPPING.matcher(content);
        while (spring.find() && endpoints.size() < MAX_PER_FILE) {
            String verb = spring.group(1).toUpperCase(Locale.ROOT);
            if ("REQUEST".equals(verb)) continue;
            endpoints.add(verb + " " + joinPath(classPrefix, spring.group(2)));
        }
        collectRoutes(EXPRESS_ROUTE, content, endpoints);
        collectRoutes(DECORATOR_ROUTE, content, endpoints);
        collectRoutes(GO_ROUTE, content, endpoints);
        if (lower.endsWith(".php") || lower.endsWith(".rb")) {
            collectRoutes(PHP_RUBY_ROUTE, content, endpoints);
        }
        if (lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".js") || lower.endsWith(".jsx")
                || lower.endsWith(".vue")) {
            Matcher ui = UI_ROUTE.matcher(content);
            while (ui.find() && endpoints.size() < MAX_PER_FILE) {
                String route = ui.group(1).strip();
                endpoints.add("UI /" + route.replaceFirst("^/", ""));
            }
        }

        collect(JPA_ENTITY, content, 1, entities);
        Matcher table = JPA_TABLE.matcher(content);
        if (table.find() && !entities.isEmpty()) {
            String first = entities.iterator().next();
            entities.remove(first);
            entities.add(first + " (" + table.group(1) + ")");
        }
        Matcher orm = ORM_MODEL.matcher(content);
        while (orm.find() && entities.size() < MAX_PER_FILE) {
            for (int g = 1; g <= orm.groupCount(); g++) {
                if (orm.group(g) != null) {
                    entities.add(orm.group(g));
                    break;
                }
            }
        }
        return result(symbols, endpoints, entities);
    }

    private static String springClassPrefix(String content) {
        Matcher m = Pattern.compile("@RequestMapping\\s*\\(\\s*(?:(?:value|path)\\s*=\\s*)?\\{?\\s*\"([^\"]*)\"[\\s\\S]{0,400}?\\bclass\\b")
                .matcher(content);
        return m.find() ? m.group(1) : "";
    }

    private static String joinPath(String prefix, String path) {
        String p = path == null ? "" : path;
        String joined = (prefix + "/" + p).replaceAll("/+", "/");
        if (joined.length() > 1 && joined.endsWith("/")) joined = joined.substring(0, joined.length() - 1);
        return joined.startsWith("/") ? joined : "/" + joined;
    }

    private static void collectRoutes(Pattern pattern, String content, Set<String> into) {
        Matcher m = pattern.matcher(content);
        while (m.find() && into.size() < MAX_PER_FILE) {
            String verb = m.group(1).toUpperCase(Locale.ROOT).replace("HTTP", "");
            if ("ROUTE".equals(verb) || "HANDLEFUNC".equals(verb) || "HANDLE".equals(verb)) verb = "ANY";
            into.add(verb + " " + m.group(2).strip());
        }
    }

    private static void collect(Pattern pattern, String content, int group, Set<String> into) {
        Matcher m = pattern.matcher(content);
        while (m.find() && into.size() < MAX_PER_FILE) {
            String value = m.group(group);
            if (value != null && !value.isBlank()) into.add(value.strip());
        }
    }

    private static FileSymbols result(Set<String> symbols, Set<String> endpoints, Set<String> entities) {
        return new FileSymbols(new ArrayList<>(symbols), new ArrayList<>(endpoints), new ArrayList<>(entities));
    }
}
