package com.example.blog.web;

import com.example.blog.domain.*;
import com.example.blog.repository.*;
import com.example.blog.service.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"blog.admin.enabled=true", "blog.admin.username=test-author", "blog.admin.password=Test-only-long-password-2026"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ReaderFeedbackWebTest {
    @Autowired MockMvc mvc;
    @Autowired PostService posts;
    @Autowired ReaderFeedbackService feedback;
    @Autowired ReaderCommentRepository comments;

    @Test void visitorCanSubmitButOnlyAdministratorCanModerateAndTextIsEscaped() throws Exception {
        var post = posts.create("读者讨论", "摘要", "正文", PostStatus.PUBLISHED, null);
        mvc.perform(post("/posts/{id}/comments", post.getId()).param("nickname", "读者").param("body", "未授权"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/posts/{id}/comments", post.getId()).with(csrf()).param("nickname", "<b>读者</b>")
            .param("body", "<script>window.commentXss=1</script>"))
            .andExpect(redirectedUrl("/posts/" + post.getId() + "#discussion"));
        mvc.perform(get("/posts/{id}", post.getId())).andExpect(content().string(not(containsString("window.commentXss"))));
        mvc.perform(get("/admin/comments")).andExpect(redirectedUrl("/login"));
        var comment = comments.findByPostIdAndStatus(post.getId(), ReaderComment.Status.PENDING,
            org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst();
        mvc.perform(post("/admin/comments/{id}/approve", comment.getId()).with(csrf()).param("version", "0"))
            .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/comments").with(user("test-author"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;script&gt;window.commentXss=1&lt;/script&gt;")));
        mvc.perform(post("/admin/comments/{id}/approve", comment.getId()).with(user("test-author")).with(csrf()).param("version", "0"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/posts/{id}", post.getId())).andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;script&gt;window.commentXss=1&lt;/script&gt;")))
            .andExpect(content().string(not(containsString("<script>window.commentXss"))));
    }
    @Test void ratingCookieIsPrivateAndVotesCanBeUpdatedWithoutLogin() throws Exception {
        var post = posts.create("打星", "摘要", "正文", PostStatus.PUBLISHED, null);
        var result = mvc.perform(get("/posts/{id}", post.getId()).secure(true)).andExpect(status().isOk()).andReturn();
        var cookie = result.getResponse().getCookie("reader_id");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        mvc.perform(post("/posts/{id}/rating", post.getId()).with(csrf()).param("stars", "5"))
            .andExpect(status().isBadRequest());
        for (int stars : new int[]{3, 5}) mvc.perform(post("/posts/{id}/rating", post.getId()).with(csrf())
            .cookie(new Cookie("reader_id", cookie.getValue())).param("stars", "" + stars))
            .andExpect(status().is3xxRedirection());
        var state = feedback.feedback(post.getId(), cookie.getValue(), 0);
        assertThat(state.votes()).isEqualTo(1);
        assertThat(state.ownStars()).isEqualTo(5);
    }
    @Test void honeypotAndInvalidCommentsDoNotCreatePublicOrPendingRows() throws Exception {
        var post = posts.create("反垃圾", "摘要", "正文", PostStatus.PUBLISHED, null);
        long before = comments.count();
        mvc.perform(post("/posts/{id}/comments", post.getId()).with(csrf()).param("nickname", "bot")
            .param("body", "spam").param("website", "spam.example"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(post("/posts/{id}/comments", post.getId()).with(csrf()).param("nickname", " ").param("body", "正文"))
            .andExpect(flash().attributeExists("feedbackError"));
        assertThat(comments.count()).isEqualTo(before);
    }
}
