package com.example.blog.service;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** The only boundary at which user-authored Markdown becomes trusted HTML. */
@Service
public class MarkdownService {
    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
        .escapeHtml(true).sanitizeUrls(true).build();
    private final Safelist allowed = Safelist.basic()
        .addTags("h1", "h2", "h3", "h4", "h5", "h6", "hr", "del")
        .addAttributes("code", "class")
        .addProtocols("a", "href", "https", "http", "mailto");

    public RenderedMarkdown render(String source) {
        String markdown = source == null ? "" : source;
        if (markdown.length() > 100000) {
            throw new IllegalArgumentException("正文不能超过100000个字符");
        }
        // Images are deliberately excluded: no remote tracking or image URL schemes.
        var document = Jsoup.parseBodyFragment(Jsoup.clean(renderer.render(parser.parse(markdown)), allowed));
        document.outputSettings().prettyPrint(false);
        List<Heading> headings = new ArrayList<>();
        for (var element : document.select("h1,h2,h3,h4,h5,h6")) {
            String id = "section-" + (headings.size() + 1);
            element.attr("id", id);
            headings.add(new Heading(id, element.text(), Integer.parseInt(element.tagName().substring(1))));
        }
        int minutes = Math.max(1, (markdown.codePointCount(0, markdown.length()) + 399) / 400);
        return new RenderedMarkdown(document.body().html(), List.copyOf(headings), minutes);
    }

    public record Heading(String id, String title, int level) { }
    public record RenderedMarkdown(String html, List<Heading> headings, int readingMinutes) { }
}
