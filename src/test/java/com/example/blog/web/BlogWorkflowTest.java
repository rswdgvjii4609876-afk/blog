package com.example.blog.web;

import com.example.blog.domain.*;
import com.example.blog.repository.*;
import com.example.blog.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlogWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired PostService service;
    @Autowired PostRepository posts;
    @Autowired PostRevisionRepository revisions;

    @Test
    void writesRequireCsrfAndReadsHaveBrowserSecurityHeaders() throws Exception {
        mvc.perform(post("/admin/posts").param("title", "bad")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/markdown/preview").contentType("text/plain").content("# test"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/admin/posts/new")).andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void previewUsesTheSameSafeRenderingAsPublishedArticle() throws Exception {
        String source = "## 章节\n\n<script>alert(1)</script>\n\n[x](javascript:alert%281%29)";
        mvc.perform(post("/admin/markdown/preview").with(csrf()).contentType("text/plain;charset=UTF-8").content(source))
            .andExpect(status().isOk()).andExpect(content().string(containsString("id=\"section-1\"")))
            .andExpect(content().string(not(containsString("<script>"))))
            .andExpect(content().string(not(containsString("href=\"javascript:"))));
        var post = service.create("标题", "摘要", source, PostStatus.PUBLISHED, null);
        mvc.perform(get("/posts/" + post.getId())).andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"section-1\"")))
            .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void restoreShowsHistoricalSnapshotAndWithdrawsPublishedContent() throws Exception {
        var post = service.create("初版", "摘要", "第一版正文", PostStatus.DRAFT, "起点");
        var first = revisions.findByPostIdOrderByNumberDesc(post.getId(), PageRequest.of(0, 10)).getContent().getFirst();
        service.update(post.getId(), post.getVersion(), "发布版", "摘要", "第二版正文", PostStatus.PUBLISHED, "完善");
        String history = "/admin/posts/" + post.getId() + "/history";
        mvc.perform(get(history)).andExpect(status().isOk()).andExpect(content().string(containsString("完善")));
        mvc.perform(get(history + "/" + first.getId())).andExpect(status().isOk())
            .andExpect(content().string(containsString("第一版正文")));
        mvc.perform(post(history + "/" + first.getId() + "/restore").with(csrf()).param("version", "1"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/posts/" + post.getId() + "/edit?restored"));
        mvc.perform(get("/posts/" + post.getId())).andExpect(status().isNotFound());
        assertThat(posts.findById(post.getId()).orElseThrow().getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    void rejectsRevisionFromAnotherArticleAndInvalidHistoryPages() throws Exception {
        var a = service.create("一", "摘要", "内容", PostStatus.DRAFT, null);
        var b = service.create("二", "摘要", "内容", PostStatus.DRAFT, null);
        var revision = revisions.findByPostIdOrderByNumberDesc(b.getId(), PageRequest.of(0, 10)).getContent().getFirst();
        String history = "/admin/posts/" + a.getId() + "/history";
        mvc.perform(get(history + "/" + revision.getId())).andExpect(status().isNotFound());
        mvc.perform(post(history + "/" + revision.getId() + "/restore").with(csrf()).param("version", "0"))
            .andExpect(status().isNotFound());
        mvc.perform(get(history).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(history).param("page", "2147483647")).andExpect(status().isBadRequest());
        mvc.perform(get(history).param("page", "100")).andExpect(status().isNotFound());
        mvc.perform(get("/posts/" + a.getId() + "/history")).andExpect(status().isNotFound());
    }

    @Test
    void staleFormKeepsSubmittedContentAndNeverOverwritesNewerRevision() throws Exception {
        var post = service.create("原文", "摘要", "原文", PostStatus.DRAFT, null);
        service.update(post.getId(), post.getVersion(), "最新", "摘要", "已更新", PostStatus.DRAFT, null);
        mvc.perform(post("/admin/posts/" + post.getId()).with(csrf())
                .param("version", "0").param("title", "待合并内容").param("summary", "摘要")
                .param("content", "我的输入不能丢失").param("status", "PUBLISHED"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("postForm", "version"))
            .andExpect(content().string(containsString("我的输入不能丢失")));
        assertThat(posts.findById(post.getId()).orElseThrow().getTitle()).isEqualTo("最新");
    }

    @Test
    void rejectsMissingVersionLongContentAndLongChangeNotes() throws Exception {
        var post = service.create("原文", "摘要", "内容", PostStatus.DRAFT, null);
        mvc.perform(post("/admin/posts/" + post.getId()).with(csrf())
            .param("title", "试图修改").param("summary", "摘要").param("content", "内容").param("status", "DRAFT"))
            .andExpect(model().attributeHasFieldErrors("postForm", "version"));
        mvc.perform(post("/admin/posts").with(csrf()).param("title", "标题").param("summary", "摘要")
            .param("content", "x".repeat(100001)).param("status", "DRAFT"))
            .andExpect(model().attributeHasFieldErrors("postForm", "content"));
        mvc.perform(post("/admin/posts").with(csrf()).param("title", "标题").param("summary", "摘要")
            .param("content", "内容").param("status", "DRAFT").param("changeNote", "x".repeat(301)))
            .andExpect(model().attributeHasFieldErrors("postForm", "changeNote"));
        mvc.perform(post("/admin/markdown/preview").with(csrf()).contentType("text/plain").content("x".repeat(100001)))
            .andExpect(status().isPayloadTooLarge());
    }
}
