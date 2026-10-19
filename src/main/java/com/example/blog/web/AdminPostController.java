package com.example.blog.web;

import jakarta.validation.Valid;
import com.example.blog.service.PostService;
import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import com.example.blog.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminPostController {

    private static final int PAGE_SIZE = 10;

    private final PostRepository postRepository;
    private final PostService postService;

    public AdminPostController(PostRepository postRepository, PostService postService) {
        this.postRepository = postRepository;
        this.postService = postService;
    }

    @GetMapping("/admin/posts")
    public String listPosts(
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
            ? postRepository.findAll(pageable)
            : postRepository.findByTitleContainingIgnoreCaseOrSummaryContainingIgnoreCase(keyword, keyword, pageable);
        if (page > 0 && page >= postPage.getTotalPages()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("postPage", postPage);
        model.addAttribute("posts", postPage.getContent());
        model.addAttribute("q", keyword);
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
        postForm.setStatus(post.getStatus());
        postForm.setVersion(post.getVersion());
        model.addAttribute("postForm", postForm);
        model.addAttribute("postId", id);
        return "admin/post-form";
    }

    @PostMapping("/admin/posts")
    public String createPost(
        @Valid @ModelAttribute("postForm") PostForm postForm,
        BindingResult bindingResult,
        RedirectAttributes redirect
    ) {
        if (bindingResult.hasErrors()) {
            return "admin/post-form";
        }
        Post saved = postService.create(postForm.getTitle(), postForm.getSummary(), postForm.getContent(),
            postForm.getStatus(), postForm.getChangeNote());
        redirect.addFlashAttribute("notice", saved.getStatus() == PostStatus.PUBLISHED ? "文章已发布。" : "草稿已保存。继续完善，再与世界分享。");
        return saved.getStatus() == PostStatus.PUBLISHED
            ? "redirect:/posts/" + saved.getId()
            : "redirect:/admin/posts";
    }

    @PostMapping("/admin/posts/{id}")
    public String updatePost(
        @PathVariable Long id,
        @Valid @ModelAttribute("postForm") PostForm postForm,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirect
    ) {
        if (postForm.getVersion() == null || postForm.getVersion() < 0) {
            bindingResult.rejectValue("version", "missing", "编辑凭据已失效，请重新打开文章编辑页");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("postId", id);
            return "admin/post-form";
        }
        Post post;
        try {
            post = postService.update(id, postForm.getVersion(), postForm.getTitle(), postForm.getSummary(),
                postForm.getContent(), postForm.getStatus(), postForm.getChangeNote());
        } catch (org.springframework.dao.OptimisticLockingFailureException concurrentWrite) {
            bindingResult.rejectValue("version", "conflict", "保存期间文章发生变化。请保留输入，在新窗口查看最新内容后重新编辑。");
            model.addAttribute("postId", id);
            return "admin/post-form";
        } catch (ResponseStatusException conflict) {
            if (conflict.getStatusCode() != HttpStatus.CONFLICT) throw conflict;
            bindingResult.rejectValue("version", "conflict", conflict.getReason());
            model.addAttribute("postId", id);
            return "admin/post-form";
        }
        redirect.addFlashAttribute("notice", "修改已保存，并已记录到文章成长时间线。");
        return post.getStatus() == PostStatus.PUBLISHED
            ? "redirect:/posts/" + id
            : "redirect:/admin/posts";
    }

    @GetMapping("/admin/posts/{id}/delete")
    public String confirmDelete(@PathVariable Long id, Model model) {
        model.addAttribute("post", postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "admin/delete";
    }

    @PostMapping("/admin/posts/{id}/delete")
    public String deletePost(@PathVariable Long id, @RequestParam long version, RedirectAttributes redirect) {
        postService.delete(id, version);
        redirect.addFlashAttribute("notice", "文章及其修订记录已删除。");
        return "redirect:/admin/posts";
    }
}
