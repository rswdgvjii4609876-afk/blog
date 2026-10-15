package com.example.blog.web;

import com.example.blog.domain.Post;
import com.example.blog.repository.PostRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminPostController {

    private final PostRepository postRepository;

    public AdminPostController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @GetMapping("/admin/posts")
    public String listPosts(Model model) {
        var posts = postRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("posts", posts);
        model.addAttribute("hasPosts", !posts.isEmpty());
        return "admin/posts";
    }

    @GetMapping("/admin/posts/new")
    public String newPostForm() {
        return "admin/post-form";
    }

    @PostMapping("/admin/posts")
    public String createPost(
        @RequestParam String title,
        @RequestParam String summary,
        @RequestParam String content
    ) {
        Post post = new Post(title, summary, content);
        Post saved = postRepository.save(post);
        return "redirect:/posts/" + saved.getId();
    }
}
