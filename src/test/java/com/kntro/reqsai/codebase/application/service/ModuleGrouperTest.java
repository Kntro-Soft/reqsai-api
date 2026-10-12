package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import com.kntro.reqsai.codebase.application.service.ModuleGrouper.ModuleDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ModuleGrouper")
class ModuleGrouperTest {

    private static SourceFile f(String path) {
        return new SourceFile(path, "x");
    }

    @Test
    @DisplayName("makes one module per folder and folds single-file folders into their parent")
    void folders() {
        List<ModuleDraft> modules = ModuleGrouper.group(List.of(
                f("src/reservations/a.ts"), f("src/reservations/b.ts"),
                f("src/payments/a.ts"), f("src/payments/b.ts"),
                f("src/utils/one.ts"), f("main.ts")), 50);

        // src/utils has one file, so it folds into src, which then has one file and folds into the root.
        assertThat(modules).extracting(ModuleDraft::path)
                .containsExactlyInAnyOrder("", "src/payments", "src/reservations");
        assertThat(modules).filteredOn(m -> m.path().isEmpty()).singleElement()
                .satisfies(m -> assertThat(m.files()).extracting(SourceFile::path)
                        .containsExactly("main.ts", "src/utils/one.ts"));
    }

    @Test
    @DisplayName("stays within the module budget by folding the smallest modules up")
    void budget() {
        List<SourceFile> files = IntStream.range(0, 30)
                .boxed()
                .flatMap(i -> List.of(f("src/m" + i + "/a.ts"), f("src/m" + i + "/b.ts")).stream())
                .toList();
        List<ModuleDraft> modules = ModuleGrouper.group(files, 10);
        assertThat(modules).hasSizeLessThanOrEqualTo(10);
        assertThat(modules.stream().mapToInt(m -> m.files().size()).sum()).isEqualTo(60);
    }
}
