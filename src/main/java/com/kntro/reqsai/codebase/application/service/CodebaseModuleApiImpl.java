package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.api.CodeModuleView;
import com.kntro.reqsai.codebase.api.CodeOverview;
import com.kntro.reqsai.codebase.api.CodebaseModuleApi;
import com.kntro.reqsai.codebase.application.port.CodeModuleRepository;
import com.kntro.reqsai.codebase.application.port.CodeRepositoryRepository;
import com.kntro.reqsai.codebase.domain.model.CodeModule;
import com.kntro.reqsai.codebase.domain.model.CodeRepository;
import com.kntro.reqsai.codebase.domain.model.CodeRepositoryStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link CodebaseModuleApi} over the indexed modules. Similarity search asks pgvector for the nearest
 * modules and keeps those above a floor, so an unrelated conversation does not drag code into the prompt;
 * without an embedding it ranks modules by the meaningful words they share with the text.
 */
@Service
@RequiredArgsConstructor
class CodebaseModuleApiImpl implements CodebaseModuleApi {

    private final CodeRepositoryRepository repositories;
    private final CodeModuleRepository modules;

    @Value("${reqsai.codebase.relevance-floor:0.20}")
    private double relevanceFloor = 0.20;

    @Override
    @Transactional(readOnly = true)
    public List<CodeModuleView> findRelevantModules(UUID projectId, float @Nullable [] queryEmbedding,
                                                    String queryText, int topK) {
        if (topK <= 0) return List.of();
        Map<UUID, CodeRepository> repos = repositories.findAllByProjectId(projectId).stream()
                .collect(Collectors.toMap(CodeRepository::getId, Function.identity()));
        if (repos.isEmpty()) return List.of();

        List<CodeModule> ranked;
        if (queryEmbedding != null) {
            ranked = modules.findNearest(projectId, queryEmbedding, topK * 2).stream()
                    .filter(m -> m.getEmbedding() != null && cosine(queryEmbedding, m.getEmbedding()) >= relevanceFloor)
                    .limit(topK)
                    .toList();
            if (ranked.isEmpty()) ranked = byWords(projectId, queryText, topK);
        } else {
            ranked = byWords(projectId, queryText, topK);
        }
        return ranked.stream()
                .filter(m -> repos.containsKey(m.getRepositoryId()))
                .map(m -> toView(m, repos.get(m.getRepositoryId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CodeOverview> findOverview(UUID projectId) {
        List<CodeRepository> ready = repositories.findAllByProjectId(projectId).stream()
                .filter(r -> r.getStatus() == CodeRepositoryStatus.READY || r.getIndexedAt() != null)
                .toList();
        if (ready.isEmpty()) return Optional.empty();
        Set<String> languages = new LinkedHashSet<>();
        Set<String> frameworks = new LinkedHashSet<>();
        Set<String> databases = new LinkedHashSet<>();
        Set<String> platforms = new LinkedHashSet<>();
        List<String> overviews = new ArrayList<>();
        for (CodeRepository repo : ready) {
            languages.addAll(repo.getProfile().languages());
            frameworks.addAll(repo.getProfile().frameworks());
            databases.addAll(repo.getProfile().databases());
            platforms.addAll(repo.getProfile().platforms());
            if (repo.getProfile().overview() != null) {
                overviews.add(ready.size() > 1 ? repo.fullName() + ": " + repo.getProfile().overview()
                        : repo.getProfile().overview());
            }
        }
        return Optional.of(new CodeOverview(ready.stream().map(CodeRepository::fullName).toList(),
                overviews.isEmpty() ? null : String.join("\n", overviews), List.copyOf(languages),
                List.copyOf(frameworks), List.copyOf(databases), List.copyOf(platforms)));
    }

    private List<CodeModule> byWords(UUID projectId, String text, int topK) {
        Set<String> query = words(text);
        if (query.isEmpty()) return List.of();
        record Scored(CodeModule module, long score) {
        }
        return modules.findAllByProjectId(projectId).stream()
                .map(m -> new Scored(m, words(CodeModule.embeddingText(m.getName(), m.getSummary(),
                        m.getCapabilities(), m.getBusinessRules())).stream().filter(query::contains).count()))
                .filter(s -> s.score() >= 2)
                .sorted(Comparator.comparingLong(Scored::score).reversed())
                .limit(topK)
                .map(Scored::module)
                .toList();
    }

    /** Lower-case, accent-free words of 4+ letters: enough to tell "reserva" from "de". */
    static Set<String> words(@Nullable String text) {
        if (text == null) return Set.of();
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        Set<String> out = new HashSet<>();
        for (String word : plain.split("[^a-z0-9]+")) {
            if (word.length() >= 4) out.add(word.length() > 6 ? word.substring(0, 6) : word);
        }
        return out;
    }

    static double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 0;
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static CodeModuleView toView(CodeModule module, CodeRepository repo) {
        return new CodeModuleView(module.getId(), repo.fullName(), module.getPath(), module.getName(),
                module.getSummary(), module.getCapabilities(), module.getBusinessRules(), module.getEndpoints(),
                repo.treeUrl(module.getPath()));
    }
}
