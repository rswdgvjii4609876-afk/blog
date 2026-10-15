package com.example.blog.web;

import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import com.example.blog.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminPostController.class)
class AdminPostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostRepository postRepository;

    @Test
    void adminPostsRendersWhenPresent() throws Exception {
        Post p1 = new Post("后台标题 1", "后台摘要 1", "后台正文 1", PostStatus.PUBLISHED);
        Post p2 = new Post("后台标题 2", "后台摘要 2", "后台正文 2", PostStatus.DRAFT);
        when(postRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of(p1, p2));

        var result = mockMvc.perform(get("/admin/posts"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/posts"))
            .andExpect(content().string(containsString("后台文章管理")))
            .andExpect(content().string(containsString("后台标题 1")))
            .andExpect(content().string(containsString("后台标题 2")))
            .andExpect(content().string(containsString("已发布")))
            .andExpect(content().string(containsString("草稿")))
            .andExpect(content().string(containsString("/admin/posts/new")))
            .andExpect(content().string(containsString("href=\"/posts/")))
            .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] articles = html.split("<article class=\"admin-item\">");
        assertThat(articles).hasSize(3);
        String publishedArticle = articles[1].split("</article>")[0];
        String draftArticle = articles[2].split("</article>")[0];
        assertThat(publishedArticle).contains("已发布", "href=\"/posts/", "编辑", "删除");
        assertThat(draftArticle).contains("草稿", "编辑", "删除").doesNotContain("href=\"/posts/", "查看");
    }

    @Test
    void adminPostsRendersEmptyStateWhenNoPosts() throws Exception {
        when(postRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of());

        mockMvc.perform(get("/admin/posts"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/posts"))
            .andExpect(content().string(containsString("暂无文章")));
    }

    @Test
    void newPostFormRenders() throws Exception {
        mockMvc.perform(get("/admin/posts/new"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/post-form"))
            .andExpect(model().attributeExists("postForm"))
            .andExpect(content().string(containsString("发布文章")))
            .andExpect(content().string(containsString("保存草稿")))
            .andExpect(content().string(containsString("name=\"status\" value=\"DRAFT\"")))
            .andExpect(content().string(containsString("name=\"status\" value=\"PUBLISHED\"")))
            .andExpect(content().string(containsString("name=\"title\"")))
            .andExpect(content().string(containsString("name=\"summary\"")))
            .andExpect(content().string(containsString("name=\"content\"")))
            .andExpect(content().string(containsString("/admin/posts")));
    }

    @Test
    void createPostSavesAndRedirectsToDetail() throws Exception {
        Post savedPost = org.mockito.Mockito.mock(Post.class);
        when(savedPost.getId()).thenReturn(42L);
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        mockMvc.perform(post("/admin/posts")
                .param("title", "测试标题")
                .param("summary", "测试摘要")
                .param("content", "测试正文")
                .param("status", "PUBLISHED"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/posts/42"));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post submitted = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("测试标题", submitted.getTitle());
        org.junit.jupiter.api.Assertions.assertEquals("测试摘要", submitted.getSummary());
        org.junit.jupiter.api.Assertions.assertEquals("测试正文", submitted.getContent());
        org.junit.jupiter.api.Assertions.assertEquals(PostStatus.PUBLISHED, submitted.getStatus());
    }

    @Test
    void editPostFormRendersWithExistingPost() throws Exception {
        Post existingPost = new Post("原始标题", "原始摘要", "原始正文", PostStatus.DRAFT);
        when(postRepository.findById(42L)).thenReturn(Optional.of(existingPost));

        mockMvc.perform(get("/admin/posts/42/edit"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/post-form"))
            .andExpect(model().attributeExists("postForm"))
            .andExpect(model().attribute("postId", 42L))
            .andExpect(content().string(containsString("原始标题")))
            .andExpect(content().string(containsString("原始摘要")))
            .andExpect(content().string(containsString("原始正文")))
            .andExpect(content().string(containsString("保存修改")))
            .andExpect(content().string(containsString("value=\"DRAFT\" selected=\"selected\"")))
            .andExpect(content().string(containsString("/admin/posts/42")));
    }

    @Test
    void editPostFormReturnsNotFoundWhenPostIsMissing() throws Exception {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/posts/999/edit"))
            .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({
        "DRAFT, PUBLISHED, /posts/42",
        "PUBLISHED, DRAFT, /admin/posts",
        "DRAFT, DRAFT, /admin/posts",
        "PUBLISHED, PUBLISHED, /posts/42"
    })
    void updatePostSavesContentAndStatusAndRedirects(PostStatus before, PostStatus after, String redirect) throws Exception {
        Post existingPost = new Post("原标题", "原摘要", "原正文", before);
        when(postRepository.findById(42L)).thenReturn(Optional.of(existingPost));

        mockMvc.perform(post("/admin/posts/42")
                .param("title", "修改后的标题")
                .param("summary", "修改后的摘要")
                .param("content", "修改后的正文")
                .param("status", after.name()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(redirect));

        org.junit.jupiter.api.Assertions.assertEquals("修改后的标题", existingPost.getTitle());
        org.junit.jupiter.api.Assertions.assertEquals("修改后的摘要", existingPost.getSummary());
        org.junit.jupiter.api.Assertions.assertEquals("修改后的正文", existingPost.getContent());
        org.junit.jupiter.api.Assertions.assertEquals(after, existingPost.getStatus());
        verify(postRepository).save(existingPost);
    }

    @Test
    void updatePostReturnsNotFoundWhenPostIsMissing() throws Exception {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/posts/999")
                .param("title", "修改后的标题")
                .param("summary", "修改后的摘要")
                .param("content", "修改后的正文")
                .param("status", "PUBLISHED"))
            .andExpect(status().isNotFound());

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void deletePostDeletesExistingPostAndRedirectsToAdminList() throws Exception {
        Post existingPost = new Post("标题", "摘要", "正文", PostStatus.DRAFT);
        when(postRepository.findById(42L)).thenReturn(Optional.of(existingPost));

        mockMvc.perform(post("/admin/posts/42/delete"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/posts"));

        verify(postRepository).delete(existingPost);
    }

    @Test
    void deletePostReturnsNotFoundWhenPostIsMissing() throws Exception {
        when(postRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/posts/999/delete"))
            .andExpect(status().isNotFound());

        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void createPostRejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/admin/posts")
                .param("title", "")
                .param("summary", "正常摘要")
                .param("content", "正常正文")
                .param("status", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/post-form"))
            .andExpect(content().string(containsString("标题不能为空")));

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createPostRejectsBlankSummary() throws Exception {
        mockMvc.perform(post("/admin/posts")
                .param("title", "正常标题")
                .param("summary", "")
                .param("content", "正常正文")
                .param("status", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/post-form"))
            .andExpect(content().string(containsString("摘要不能为空")));

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createPostRejectsBlankContent() throws Exception {
        mockMvc.perform(post("/admin/posts")
                .param("title", "正常标题")
                .param("summary", "正常摘要")
                .param("content", "")
                .param("status", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/post-form"))
            .andExpect(content().string(containsString("正文不能为空")));

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createPostRejectsTitleLongerThan100Characters() throws Exception {
        mockMvc.perform(post("/admin/posts")
                .param("title", "a".repeat(101))
                .param("summary", "正常摘要")
                .param("content", "正常正文")
                .param("status", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/post-form"))
            .andExpect(content().string(containsString("标题不能超过100个字符")));

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createPostRejectsSummaryLongerThan300Characters() throws Exception {
        mockMvc.perform(post("/admin/posts")
                .param("title", "正常标题")
                .param("summary", "a".repeat(301))
                .param("content", "正常正文")
                .param("status", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/post-form"))
            .andExpect(content().string(containsString("摘要不能超过300个字符")));

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void updatePostRejectsInvalidInputAndKeepsEditFormAction() throws Exception {
        mockMvc.perform(post("/admin/posts/42")
                .param("title", "")
                .param("summary", "正常摘要")
                .param("content", "正常正文")
                .param("status", "PUBLISHED"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/post-form"))
            .andExpect(content().string(containsString("标题不能为空")))
            .andExpect(content().string(containsString("value=\"PUBLISHED\" selected=\"selected\"")))
            .andExpect(content().string(containsString("action=\"/admin/posts/42\"")));

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createDraftSavesAndRedirectsToAdminList() throws Exception {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/admin/posts")
                .param("title", "草稿标题")
                .param("summary", "草稿摘要")
                .param("content", "草稿正文")
                .param("status", "DRAFT"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/posts"));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(captor.getValue().getTitle()).isEqualTo("草稿标题");
        assertThat(captor.getValue().getSummary()).isEqualTo("草稿摘要");
        assertThat(captor.getValue().getContent()).isEqualTo("草稿正文");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "INVALID")
    void createAndUpdateRejectMissingOrInvalidStatus(String submittedStatus) throws Exception {
        for (String path : List.of("/admin/posts", "/admin/posts/42")) {
            var request = post(path)
                .param("title", "正常标题")
                .param("summary", "正常摘要")
                .param("content", "正常正文");
            if (submittedStatus != null) {
                request.param("status", submittedStatus);
            }

            mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("admin/post-form"))
                .andExpect(model().attributeHasFieldErrors("postForm", "status"))
                .andExpect(content().string(containsString("id=\"status-error\"")))
                .andExpect(content().string(containsString("action=\"" + path + "\"")));
        }

        verify(postRepository, never()).save(any(Post.class));
    }
}
