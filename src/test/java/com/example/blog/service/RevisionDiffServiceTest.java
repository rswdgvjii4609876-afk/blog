package com.example.blog.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RevisionDiffServiceTest {
    private final RevisionDiffService diff = new RevisionDiffService();
    @Test void identifiesEditsAndPreservesLineNumbersAndUnsafeTextAsData() {
        var result = diff.compare("intro\nold\nend", "intro\n<script>alert(1)</script>\nend");
        assertThat(result.added()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.lines()).extracting(RevisionDiffService.DiffLine::kind)
            .containsExactly("same", "removed", "added", "same");
        assertThat(result.lines().get(2).newNumber()).isEqualTo(2);
        assertThat(result.lines().get(2).text()).isEqualTo("<script>alert(1)</script>");
    }
    @Test void handlesEmptyIdenticalAndCrlfSources() {
        assertThat(diff.compare("", "line").added()).isEqualTo(1);
        assertThat(diff.compare("line", "").removed()).isEqualTo(1);
        assertThat(diff.compare(null, "").lines()).isEmpty();
        var result = diff.compare("a\r\nb\n", "a\nb\n");
        assertThat(result.added() + result.removed()).isZero();
        assertThat(result.lines()).hasSize(3);
    }
    @Test void boundsQuadraticWorkInsteadOfSilentlyTruncatingContent() {
        assertThat(diff.compare("line\n".repeat(601), "short").tooLarge()).isTrue();
        assertThat(diff.compare("line\n".repeat(599), "short").tooLarge()).isFalse();
    }
}
