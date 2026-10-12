package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Groups source files into modules — the units the copilot reasons about. A module starts as a folder
 * holding source files; folders with too few files fold into their parent so the map is not a dust of
 * one-file modules, and when there are still more than the budget allows, the smallest fold up again.
 */
public final class ModuleGrouper {

    /** A folder with fewer source files than this folds into its parent. */
    static final int MIN_FILES = 2;

    private ModuleGrouper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** A module to describe: its folder ({@code ""} for the repository root) and its files. */
    public record ModuleDraft(String path, List<SourceFile> files) {
    }

    public static List<ModuleDraft> group(List<SourceFile> sources, int maxModules) {
        Map<String, List<SourceFile>> byFolder = new TreeMap<>();
        for (SourceFile file : sources) {
            byFolder.computeIfAbsent(parent(file.path()), k -> new ArrayList<>()).add(file);
        }

        // Fold small folders into their parent, deepest first, until each folder is big enough or root.
        boolean changed = true;
        while (changed) {
            changed = false;
            String smallest = byFolder.entrySet().stream()
                    .filter(e -> !e.getKey().isEmpty() && e.getValue().size() < MIN_FILES)
                    .map(Map.Entry::getKey)
                    .max(Comparator.comparingInt(ModuleGrouper::depth).thenComparing(Comparator.naturalOrder()))
                    .orElse(null);
            if (smallest != null) {
                fold(byFolder, smallest);
                changed = true;
            }
        }

        // Respect the budget: fold the smallest (then deepest) modules up until it fits.
        while (byFolder.size() > Math.max(1, maxModules)) {
            String candidate = byFolder.entrySet().stream()
                    .filter(e -> !e.getKey().isEmpty())
                    .min(Comparator.<Map.Entry<String, List<SourceFile>>>comparingInt(e -> e.getValue().size())
                            .thenComparing(e -> -depth(e.getKey()))
                            .thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (candidate == null) break;
            fold(byFolder, candidate);
        }

        List<ModuleDraft> drafts = new ArrayList<>();
        byFolder.forEach((path, files) -> {
            List<SourceFile> sorted = new ArrayList<>(files);
            sorted.sort(Comparator.comparing(SourceFile::path));
            drafts.add(new ModuleDraft(path, List.copyOf(sorted)));
        });
        return drafts;
    }

    private static void fold(Map<String, List<SourceFile>> byFolder, String folder) {
        List<SourceFile> files = byFolder.remove(folder);
        byFolder.computeIfAbsent(parent(folder), k -> new ArrayList<>()).addAll(files);
    }

    static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static int depth(String path) {
        return path.isEmpty() ? 0 : path.split("/").length;
    }
}
