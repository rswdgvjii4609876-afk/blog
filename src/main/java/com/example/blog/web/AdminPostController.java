package com.example.blog.web;

import jakarta.validation.Valid;
import com.example.blog.domain.Post;
import com.example.blog.repository.PostRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

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
    public String newPostForm(Model model) {
        model.addAttribute("postForm", new PostForm());
        return "admin/post-form";
    }

    @GetMapping("/admin/posts/{id}/edit")
    public String editPostForm(@PathVariable Long id, Model model) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PostForm postForm = new PostForm();
        postForm.setTitle(post.getTitle());
        postForm.setSummary(post.getSummary());
        postForm.setContent(post.getContent());
        model.addAttribute("postForm", postForm);
        model.addAttribute("postId", id);
        return "admin/post-form";
    }

    @PostMapping("/admin/posts")
    public String createPost(
        @Valid @ModelAttribute("postForm") PostForm postForm,
        BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            return "admin/post-form";
        }
        Post post = new Post(postForm.getTitle(), postForm.getSummary(), postForm.getContent());
        Post saved = postRepository.save(post);
        return "redirect:/posts/" + saved.getId();
    }

    @PostMapping("/admin/posts/{id}")
    public String updatePost(
        @PathVariable Long id,
        @Valid @ModelAttribute("postForm") PostForm postForm,
        BindingResult bindingResult,
        Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("postId", id);
            return "admin/post-form";
        }
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        post.update(postForm.getTitle(), postForm.getSummary(), postForm.getContent());
        postRepository.save(post);
        return "redirect:/posts/" + id;
    }

    @PostMapping("/admin/posts/{id}/delete")
    public String deletePost(@PathVariable Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        postRepository.delete(post);
        return "redirect:/admin/posts";
    }
}
