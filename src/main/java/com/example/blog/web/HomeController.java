package com.example.blog.web;

import com.example.blog.domain.Post;
import com.example.blog.repository.PostRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 首页控制器：列出所有文章（按 id 升序，便于看效果）。
 */
@Controller
public class HomeController {

    private final PostRepository postRepository;

    public HomeController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        var posts = postRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("posts", posts);
        model.addAttribute("hasPosts", !posts.isEmpty());
        model.addAttribute("postCount", posts.size());
        return "index";
    }

    @GetMapping("/posts/{id}")
    public String post(@PathVariable Long id, Model model) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("post", post);
        return "post";
    }
}
