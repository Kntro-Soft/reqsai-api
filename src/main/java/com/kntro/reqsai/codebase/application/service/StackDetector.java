package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import com.kntro.reqsai.codebase.domain.model.CodeProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Detects the technical profile of a repository deterministically: languages from the source files,
 * frameworks and databases from the dependencies its manifests declare, and the client platforms they
 * imply. The names match how the project profile already lists them (e.g. "Angular", "PostgreSQL").
 */
public final class StackDetector {

    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("kts", "Kotlin"),
            Map.entry("scala", "Scala"), Map.entry("groovy", "Groovy"), Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"), Map.entry("js", "JavaScript"), Map.entry("jsx", "JavaScript"),
            Map.entry("mjs", "JavaScript"), Map.entry("cjs", "JavaScript"), Map.entry("vue", "Vue"),
            Map.entry("svelte", "Svelte"), Map.entry("py", "Python"), Map.entry("rb", "Ruby"),
            Map.entry("php", "PHP"), Map.entry("go", "Go"), Map.entry("rs", "Rust"), Map.entry("cs", "C#"),
            Map.entry("fs", "F#"), Map.entry("swift", "Swift"), Map.entry("m", "Objective-C"),
            Map.entry("dart", "Dart"), Map.entry("sql", "SQL"), Map.entry("ex", "Elixir"),
            Map.entry("exs", "Elixir"), Map.entry("clj", "Clojure"), Map.entry("lua", "Lua"));

    /** Dependency name fragment → framework, matched against manifest contents (lower case). */
    private static final Map<String, String> FRAMEWORKS = orderedMap(
            "\"@angular/core\"", "Angular", "\"react-native\"", "React Native", "\"next\"", "Next.js",
            "\"react\"", "React", "\"nuxt\"", "Nuxt", "\"vue\"", "Vue", "\"svelte\"", "Svelte",
            "\"@nestjs/core\"", "NestJS", "\"express\"", "Express", "\"fastify\"", "Fastify",
            "\"@ionic/", "Ionic", "\"electron\"", "Electron", "\"vite\"", "Vite", "\"tailwindcss\"", "Tailwind CSS",
            "\"@spartan-ng/", "Spartan UI", "spring-boot", "Spring Boot", "quarkus", "Quarkus",
            "micronaut", "Micronaut", "django", "Django", "fastapi", "FastAPI", "flask", "Flask",
            "laravel/framework", "Laravel", "symfony/", "Symfony", "rails", "Ruby on Rails",
            "github.com/gin-gonic/gin", "Gin", "github.com/labstack/echo", "Echo", "github.com/gofiber/fiber", "Fiber",
            "microsoft.aspnetcore", "ASP.NET Core", "flutter:", "Flutter", "actix-web", "Actix Web");

    private static final Map<String, String> DATABASES = orderedMap(
            "pgvector", "pgvector", "postgres", "PostgreSQL", "\"pg\"", "PostgreSQL", "psycopg", "PostgreSQL",
            "mysql", "MySQL", "mariadb", "MariaDB", "mongodb", "MongoDB", "mongoose", "MongoDB",
            "redis", "Redis", "sqlite", "SQLite", "sqlserver", "SQL Server", "mssql", "SQL Server",
            "oracle", "Oracle", "firebase", "Firebase", "supabase", "Supabase", "dynamodb", "DynamoDB",
            "elasticsearch", "Elasticsearch", "cassandra", "Cassandra", "neo4j", "Neo4j", "h2database", "H2");

    private StackDetector() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static CodeProfile detect(List<SourceFile> files) {
        Map<String, Integer> languageCounts = new HashMap<>();
        StringBuilder manifests = new StringBuilder();
        Set<String> platforms = new LinkedHashSet<>();
        boolean hasHtmlUi = false;
        for (SourceFile file : files) {
            String path = file.path().toLowerCase(Locale.ROOT);
            if (SourceFilter.isManifest(path)) {
                manifests.append('\n').append(file.content().toLowerCase(Locale.ROOT));
                if (path.endsWith("androidmanifest.xml")) platforms.add("Android");
                if (path.endsWith("podfile")) platforms.add("iOS");
            } else if (SourceFilter.isSource(path)) {
                String ext = path.substring(path.lastIndexOf('.') + 1);
                String language = LANGUAGES.get(ext);
                if (language != null) languageCounts.merge(language, 1, Integer::sum);
                if (path.endsWith(".html") || path.endsWith(".tsx") || path.endsWith(".jsx")
                        || path.endsWith(".vue") || path.endsWith(".svelte")) {
                    hasHtmlUi = true;
                }
            }
        }

        String deps = manifests.toString();
        Set<String> frameworks = new LinkedHashSet<>();
        FRAMEWORKS.forEach((needle, framework) -> {
            if (deps.contains(needle)) frameworks.add(framework);
        });
        if (frameworks.contains("Next.js") || frameworks.contains("React Native")) {
            frameworks.remove("React");
            frameworks.add("React");
        }
        Set<String> databases = new LinkedHashSet<>();
        DATABASES.forEach((needle, database) -> {
            if (deps.contains(needle)) databases.add(database);
        });

        if (frameworks.contains("React Native") || frameworks.contains("Flutter") || frameworks.contains("Ionic")) {
            platforms.add("Android");
            platforms.add("iOS");
        }
        if (frameworks.contains("Electron")) platforms.add("Desktop");
        if (hasHtmlUi || frameworks.stream().anyMatch(f -> List.of("Angular", "React", "Vue", "Svelte", "Next.js",
                "Nuxt").contains(f))) {
            platforms.add("Web");
        }

        int total = languageCounts.values().stream().mapToInt(Integer::intValue).sum();
        List<String> languages = new ArrayList<>();
        languageCounts.entrySet().stream()
                .filter(e -> e.getValue() >= 3 || (total > 0 && e.getValue() * 20 >= total))
                .filter(e -> !"SQL".equals(e.getKey()) || languageCounts.size() == 1)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> languages.add(e.getKey()));

        return new CodeProfile(languages, new ArrayList<>(frameworks), new ArrayList<>(databases),
                new ArrayList<>(platforms), null);
    }

    private static Map<String, String> orderedMap(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
