package com.example.blog.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MarkdownServiceTest {
    private final MarkdownService markdown = new MarkdownService();

    @Test
    void rendersCodeListsAndStableUniqueHeadingAnchors() {
        var result = markdown.render("## 重复\n\n## 重复\n\n- item\n\n```java\nSystem.out.println(1);\n``` ");
        assertThat(result.html()).contains("<h2 id=\"section-1\">重复</h2>",
            "<h2 id=\"section-2\">重复</h2>", "<ul>", "language-java", "System.out.println(1)");
        assertThat(result.headings()).hasSize(2);
        assertThat(result.readingMinutes()).isEqualTo(1);
    }

    @Test
    void neutralizesRawHtmlDangerousLinksAndRemoteImages() {
        var html = markdown.render("<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>\n\n[x](javascript:alert%281%29)\n\n![track](https://tracker.example/pixel)\n\n[safe](https://example.com)").html();
        assertThat(html).doesNotContain("<script", "<img", "href=\"javascript:", "<iframe")
            .contains("&lt;script&gt;", "href=\"https://example.com\"", "rel=\"nofollow\"");
    }

    @Test
    void boundsWorkAndHandlesEmptySource() {
        assertThat(markdown.render(null).html()).isEmpty();
        assertThatThrownBy(() -> markdown.render("x".repeat(100001)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
