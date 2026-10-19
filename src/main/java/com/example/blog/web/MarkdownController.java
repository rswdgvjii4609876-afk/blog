package com.example.blog.web;

import com.example.blog.service.MarkdownService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class MarkdownController {
    private final MarkdownService markdown;

    public MarkdownController(MarkdownService markdown) { this.markdown = markdown; }

    @PostMapping(value = "/admin/markdown/preview", consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = "text/html;charset=UTF-8")
    @ResponseBody
    public String preview(@RequestBody String source) {
        if (source.length() > 100000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        }
        return markdown.render(source).html();
    }
}
