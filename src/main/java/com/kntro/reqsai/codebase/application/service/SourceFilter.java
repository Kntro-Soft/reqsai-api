package com.kntro.reqsai.codebase.application.service;

import java.util.Locale;
import java.util.Set;

/**
 * Decides which files of a repository ReqsAI reads. It keeps source code (to map modules), manifests
 * (to detect the stack) and README files (for the overview), and leaves out dependencies, build output,
 * generated and minified files, lockfiles, binaries and anything that usually holds secrets.
 */
public final class SourceFilter {

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            "node_modules", "vendor", "dist", "build", "out", "target", "bin", "obj", ".git", ".github",
            ".idea", ".vscode", "coverage", ".next", ".nuxt", ".angular", ".cache", ".gradle", ".mvn",
            "__pycache__", ".venv", "venv", "env", ".tox", "pods", "deriveddata", ".dart_tool", ".expo",
            "storybook-static", "generated", "__generated__", "tmp", "temp", "logs");

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            "java", "kt", "kts", "scala", "groovy", "ts", "tsx", "js", "jsx", "mjs", "cjs", "vue", "svelte",
            "py", "rb", "php", "go", "rs", "cs", "fs", "swift", "m", "mm", "dart", "sql", "graphql", "gql",
            "proto", "html", "ex", "exs", "clj", "lua");

    private static final Set<String> MANIFESTS = Set.of(
            "package.json", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "pom.xml", "requirements.txt", "pyproject.toml", "pipfile", "setup.py", "go.mod", "composer.json",
            "gemfile", "pubspec.yaml", "cargo.toml", "dockerfile", "docker-compose.yml", "docker-compose.yaml",
            "compose.yml", "compose.yaml", "androidmanifest.xml", "podfile", "angular.json", "vite.config.ts",
            "vite.config.js", "next.config.js", "next.config.mjs", "nuxt.config.ts", "app.json");

    private static final Set<String> SKIPPED_FILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "bun.lock", "bun.lockb", "poetry.lock",
            "gemfile.lock", "composer.lock", "cargo.lock", "go.sum", "podfile.lock", "pubspec.lock");

    private SourceFilter() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** Whether the file is read at all. */
    public static boolean keep(String path) {
        String normalized = path.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        String[] parts = lower.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (SKIPPED_DIRECTORIES.contains(parts[i])) return false;
        }
        String file = parts[parts.length - 1];
        if (file.isEmpty() || SKIPPED_FILES.contains(file) || mayHoldSecrets(file)) return false;
        if (file.endsWith(".min.js") || file.endsWith(".min.css") || file.endsWith(".map")
                || file.endsWith(".d.ts") || file.endsWith(".spec.snap") || file.contains(".generated.")) {
            return false;
        }
        return isSource(lower) || isData(lower) || isManifest(lower) || isReadme(lower) || file.endsWith(".csproj");
    }

    /** Folders whose JSON / YAML files hold product data (texts, prices, limits) rather than tooling config. */
    private static final Set<String> DATA_FOLDERS = Set.of("src", "app", "lib", "config", "data", "i18n", "locales",
            "locale", "lang", "messages", "resources", "assets", "public");

    /**
     * A JSON or YAML file inside the application's own folders (translations, prices, catalogs, feature
     * configuration): business values often live there, so it is read as part of its module. Manifests and
     * lockfiles at the root are not data files.
     */
    public static boolean isData(String path) {
        String lower = path.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        if (!(lower.endsWith(".json") || lower.endsWith(".yml") || lower.endsWith(".yaml"))) return false;
        if (isManifest(lower) || lower.startsWith(".") || lower.contains("/.")) return false;
        String[] parts = lower.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (DATA_FOLDERS.contains(parts[i])) return true;
        }
        return false;
    }

    /** Source code that belongs to a module. */
    public static boolean isSource(String path) {
        String file = fileName(path);
        int dot = file.lastIndexOf('.');
        return dot > 0 && SOURCE_EXTENSIONS.contains(file.substring(dot + 1));
    }

    public static boolean isManifest(String path) {
        String file = fileName(path);
        return MANIFESTS.contains(file) || file.endsWith(".csproj");
    }

    /** README files at any level (README.md, readme.rst, …). */
    public static boolean isReadme(String path) {
        return fileName(path).startsWith("readme");
    }

    /** Test files are read for symbols but are not where business rules should be taken from first. */
    public static boolean isTest(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("/test/") || lower.contains("/tests/") || lower.contains("__tests__")
                || lower.startsWith("test/") || lower.startsWith("tests/")
                || lower.matches(".*[._-](spec|test)s?\\.[a-z]+$") || lower.matches(".*/test_[^/]+\\.py$");
    }

    private static boolean mayHoldSecrets(String file) {
        return file.startsWith(".env") || file.endsWith(".pem") || file.endsWith(".key") || file.endsWith(".p12")
                || file.endsWith(".pfx") || file.endsWith(".jks") || file.endsWith(".keystore")
                || file.startsWith("id_rsa") || file.startsWith("id_ed25519") || file.contains("secret")
                || file.contains("credential") || file.endsWith(".tfvars") || file.endsWith(".tfstate");
    }

    private static String fileName(String path) {
        String lower = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        int slash = lower.lastIndexOf('/');
        return slash < 0 ? lower : lower.substring(slash + 1);
    }
}
