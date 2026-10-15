package com.example.blog.web;

import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import com.example.blog.repository.PostRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 公开文章控制器：只展示已发布文章，列表按 id 升序。
 */
@Controller
public class HomeController {

    private final PostRepository postRepository;

    public HomeController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        var posts = postRepository.findByStatus(PostStatus.PUBLISHED, Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("posts", posts);
        model.addAttribute("hasPosts", !posts.isEmpty());
        model.addAttribute("postCount", posts.size());
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
