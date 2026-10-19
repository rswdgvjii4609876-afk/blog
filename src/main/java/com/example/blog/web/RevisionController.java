package com.example.blog.web;

import com.example.blog.domain.Post;
import com.example.blog.repository.PostRepository;
import com.example.blog.repository.PostRevisionRepository;
import com.example.blog.service.MarkdownService;
import com.example.blog.service.PostService;
import com.example.blog.service.RevisionDiffService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/posts/{postId}/history")
public class RevisionController {
    private final PostRepository posts;
    private final PostRevisionRepository revisions;
    private final PostService service;
    private final MarkdownService markdown;
    private final RevisionDiffService diff;

    public RevisionController(PostRepository posts, PostRevisionRepository revisions,
                              PostService service, MarkdownService markdown, RevisionDiffService diff) {
        this.posts = posts;
        this.revisions = revisions;
        this.service = service;
        this.markdown = markdown;
        this.diff = diff;
    }

    @GetMapping
    public String history(@PathVariable Long postId, @RequestParam(defaultValue = "0") int page, Model model) {
        if (page < 0 || (long) page * 10 > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        model.addAttribute("post", post(postId));
        var revisionPage = revisions.findByPostIdOrderByNumberDesc(postId, PageRequest.of(page, 10));
        if (page > 0 && page >= revisionPage.getTotalPages()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("revisionPage", revisionPage);
        return "admin/history";
    }

    @GetMapping("/{revisionId}")
    public String revision(@PathVariable Long postId, @PathVariable Long revisionId, Model model) {
        model.addAttribute("post", post(postId));
        var revision = revisions.findByIdAndPostId(revisionId, postId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("revision", revision);
        model.addAttribute("rendered", markdown.render(revision.getContent()));
        return "admin/revision";
    }

    @GetMapping("/{revisionId}/compare")
    public String compare(@PathVariable Long postId, @PathVariable Long revisionId, Model model) {
        var current = post(postId);
        var revision = revisions.findByIdAndPostId(revisionId, postId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("post", current);
        model.addAttribute("revision", revision);
        model.addAttribute("comparison", diff.compare(revision.getContent(), current.getContent()));
        return "admin/compare";
    }

    @PostMapping("/{revisionId}/restore")
    public String restore(@PathVariable Long postId, @PathVariable Long revisionId, @RequestParam long version) {
        service.restore(postId, revisionId, version);
        return "redirect:/admin/posts/" + postId + "/edit?restored";
    }

    private Post post(Long id) {
        return posts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
