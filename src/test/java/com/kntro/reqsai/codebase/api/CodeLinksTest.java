package com.kntro.reqsai.codebase.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Codebase: GitHub links to the client's code")
class CodeLinksTest {

    @Test
    @DisplayName("a blob link names the commit, the file and the highlighted lines")
    void blobUrlWithRange() {
        assertThat(CodeLinks.blobUrl("https://github.com/Kntro-Soft/reqsai-landing", "abc1234def",
                "src/lib/plans.ts", 12, 20))
                .isEqualTo("https://github.com/Kntro-Soft/reqsai-landing/blob/abc1234def/src/lib/plans.ts#L12-L20");
    }

    @Test
    @DisplayName("one line gets a single anchor, no lines get none, and a trailing slash is ignored")
    void anchors() {
        assertThat(CodeLinks.blobUrl("https://github.com/a/b/", "sha", "x.ts", 5, 5)).endsWith("/blob/sha/x.ts#L5");
        assertThat(CodeLinks.blobUrl("https://github.com/a/b", "sha", "x.ts", 5, null)).endsWith("x.ts#L5");
        assertThat(CodeLinks.blobUrl("https://github.com/a/b", "sha", "x.ts", null, null)).endsWith("/blob/sha/x.ts");
    }

    @Test
    @DisplayName("path segments are encoded, slashes are kept")
    void encodesSegments() {
        assertThat(CodeLinks.blobUrl("https://github.com/a/b", "sha", "src/my file#1.ts", 1, 2))
                .isEqualTo("https://github.com/a/b/blob/sha/src/my%20file%231.ts#L1-L2");
    }
}
