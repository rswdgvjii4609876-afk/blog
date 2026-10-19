package com.example.blog.web;

import com.example.blog.domain.Post;
import com.example.blog.service.MarkdownService;
import com.example.blog.service.ReaderFeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.blog.domain.PostStatus;
import com.example.blog.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * 公开文章控制器：只展示已发布文章，按创建时间和 id 倒序分页。
 */
@Controller
public class HomeController {

    private static final int PAGE_SIZE = 5;

    private final PostRepository postRepository;
    private final MarkdownService markdown;
    private final ReaderFeedbackService feedback;
    private final com.example.blog.service.CommunityService community;

    public HomeController(PostRepository postRepository, MarkdownService markdown, ReaderFeedbackService feedback, com.example.blog.service.CommunityService community) {
        this.postRepository = postRepository;
        this.markdown = markdown;
        this.feedback = feedback;
        this.community = community;
    }

    @GetMapping("/")
    public String index(
        @RequestParam(name = "q", defaultValue = "") String q,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(defaultValue = "0") int wallPage,
        HttpServletRequest request, HttpServletResponse response, Model model
    ) {
        String keyword = q.strip();
        if (keyword.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        // JPA 的分页偏移使用 int。
        if (page < 0 || (long) page * PAGE_SIZE > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        var pageable = PageRequest.of(page, PAGE_SIZE,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        var postPage = keyword.isEmpty()
            ? postRepository.findByStatus(PostStatus.PUBLISHED, pageable)
            : postRepository.searchByStatus(PostStatus.PUBLISHED, keyword, pageable);
        if (page > 0 && page >= postPage.getTotalPages()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("postPage", postPage);
        model.addAttribute("posts", postPage.getContent());
        model.addAttribute("q", keyword);
        var home = community.home(ReaderIdentity.ensure(request, response), wallPage);
        model.addAttribute("community", home);
        model.addAttribute("welcome", home == null ? com.example.blog.service.CommunityService.DEFAULT_WELCOME : home.settings().welcome());
        if (page == 0 && keyword.isEmpty() && postPage.hasContent()) {
            String text = org.jsoup.Jsoup.parse(markdown.render(postPage.getContent().getFirst().getContent()).html()).text();
            model.addAttribute("latestExcerpt", text.substring(0, Math.min(700, text.length())));
        }
        return "index";
    }

    @GetMapping("/posts/{id}")
    public String post(@PathVariable Long id, @RequestParam(defaultValue = "0") int commentPage,
                       HttpServletRequest request, HttpServletResponse response, Model model) {
        Post post = postRepository.findByIdAndStatus(id, PostStatus.PUBLISHED)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("post", post);
        var discussion = feedback.feedback(id, ReaderIdentity.ensure(request, response), commentPage);
        if (discussion != null && commentPage > 0 && commentPage >= discussion.comments().getTotalPages()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("feedback", discussion);
        model.addAttribute("rendered", markdown.render(post.getContent()));
        return "post";
    }
}
