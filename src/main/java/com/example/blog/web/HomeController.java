package com.example.blog.web;

import com.example.blog.domain.Post;
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

    public HomeController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @GetMapping("/")
    public String index(
        @RequestParam(name = "q", defaultValue = "") String q,
        @RequestParam(name = "page", defaultValue = "0") int page,
        Model model
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
        return "index";
    }

    @GetMapping("/posts/{id}")
    public String post(@PathVariable Long id, Model model) {
        Post post = postRepository.findByIdAndStatus(id, PostStatus.PUBLISHED)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("post", post);
        return "post";
    }
}
