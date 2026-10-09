package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import com.kntro.reqsai.codebase.application.service.ModuleGrouper.ModuleDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CodeIndexer excerpts")
class CodeIndexerExcerptTest {

    @Test
    @DisplayName("every file of a large module gets a share of the excerpt")
    void spreadsTheBudget() {
        List<SourceFile> files = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            files.add(new SourceFile("src/components/Feature" + i + ".tsx", "export const feature" + i + " = 1;\n"
                    + "// filler\n".repeat(400)));
        }

        String excerpts = CodeIndexer.excerpts(new ModuleDraft("src/components", files));

        for (int i = 0; i < 12; i++) assertThat(excerpts).contains("export const feature" + i);
    }

    @Test
    @DisplayName("a JSON data file is reduced to its values, a YAML one to its value-bearing lines")
    void dataFiles() {
        String json = CodeIndexer.dataExcerpt(new SourceFile("src/i18n/es.json",
                "{\"pricing\": {\"pro\": {\"price\": 49, \"cta\": \"Empieza ahora\"}}}"));
        String yaml = CodeIndexer.dataExcerpt(new SourceFile("config/plans.yml",
                "plans:\n  pro:\n    description: El plan para equipos\n    price: 49\n"));

        assertThat(json).isEqualTo("pricing.pro.price = 49");
        assertThat(yaml).contains("price: 49").doesNotContain("plans:");
    }
}
