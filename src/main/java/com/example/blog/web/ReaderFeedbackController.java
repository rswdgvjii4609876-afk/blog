package com.example.blog.web;

import com.example.blog.domain.ReaderComment;
import com.example.blog.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReaderFeedbackController {
    private final ReaderFeedbackService feedback;
    private final ReaderWriteLimiter limiter;
    public ReaderFeedbackController(ReaderFeedbackService feedback, ReaderWriteLimiter limiter) {
        this.feedback = feedback; this.limiter = limiter;
    }
    @PostMapping("/posts/{id}/comments")
    public String comment(@PathVariable Long id, @RequestParam String nickname, @RequestParam String body,
                          @RequestParam(defaultValue = "") String website, HttpServletRequest request,
                          RedirectAttributes redirect) {
        limiter.check(request.getRemoteAddr(), true);
        if (website.isEmpty()) {
            if (nickname.isBlank() || nickname.strip().length() > 40 || body.isBlank() || body.strip().length() > 2000) {
                redirect.addFlashAttribute("feedbackError", "请填写昵称（1～40 字）和评论（1～2000 字）。");
                redirect.addFlashAttribute("commentNickname", nickname.substring(0, Math.min(40, nickname.length())));
                redirect.addFlashAttribute("commentBody", body.substring(0, Math.min(2000, body.length())));
                return "redirect:/posts/" + id + "#discussion";
            }
            feedback.comment(id, nickname, body);
        }
        redirect.addFlashAttribute("feedbackNotice", "评论已提交，审核通过后会公开显示。");
        return "redirect:/posts/" + id + "#discussion";
    }
    @PostMapping("/posts/{id}/rating")
    public String rate(@PathVariable Long id, @RequestParam int stars, HttpServletRequest request,
                       RedirectAttributes redirect) {
        limiter.check(request.getRemoteAddr(), false);
        String reader = ReaderIdentity.read(request);
        if (reader == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先打开文章并允许 Cookie。");
        feedback.rate(id, reader, stars);
        redirect.addFlashAttribute("feedbackNotice", "评分已保存，感谢你的反馈。同一浏览器再次打星会更新原评分。");
        return "redirect:/posts/" + id + "#discussion";
    }
    @GetMapping("/admin/comments")
    public String moderation(@RequestParam(defaultValue = "PENDING") ReaderComment.Status status,
                             @RequestParam(defaultValue = "0") int page, Model model) {
        var comments = feedback.moderation(status, page);
        if (page > 0 && page >= comments.getTotalPages()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("commentPage", comments);
        model.addAttribute("filterStatus", status);
        return "admin/comments";
    }
    @PostMapping("/admin/comments/{id}/{action}")
    public String moderate(@PathVariable Long id, @PathVariable String action, @RequestParam long version,
                           RedirectAttributes redirect) {
        feedback.moderate(id, version, action);
        redirect.addFlashAttribute("notice", "评论处理完成。");
        return "redirect:/admin/comments";
    }
}
