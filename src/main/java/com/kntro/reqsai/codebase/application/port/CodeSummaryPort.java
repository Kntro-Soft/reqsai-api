package com.kntro.reqsai.codebase.application.port;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The AI that reads a module of the client's code and says what it does, which capabilities it offers
 * and which business rules it implements, plus a short overview of the whole product. The code it reads
 * is untrusted data, never instructions.
 */
public interface CodeSummaryPort {

    boolean isAvailable();

    ModuleSummary summarizeModule(ModuleDigest digest);

    /** A few sentences on what the product does, or null when the model has nothing reliable to say. */
    @Nullable String summarizeOverview(OverviewDigest digest);

    /**
     * What the model reads about one module: its files and symbols, and excerpts of its most telling
     * files (secrets already removed).
     */
    record ModuleDigest(String repository, String path, List<String> files, List<String> symbols,
                        List<String> endpoints, List<String> entities, String excerpts, String language) {
    }

    record ModuleSummary(String name, String summary, List<String> capabilities, List<String> businessRules) {
    }

    record OverviewDigest(String repository, @Nullable String readme, List<String> modules, String language) {
    }
}
