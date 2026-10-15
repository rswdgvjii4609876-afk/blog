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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
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

    private static final Sort POST_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostRepository postRepository;

    @Test
    void adminPostsRendersWhenPresent() throws Exception {
        var pageable = PageRequest.of(0, 10, POST_SORT);
        var posts = IntStream.rangeClosed(1, 10)
            .mapToObj(i -> new Post("后台标题 " + i, "后台摘要 " + i, "后台正文 " + i,
                i % 2 == 0 ? PostStatus.DRAFT : PostStatus.PUBLISHED))
            .toList();
        var postPage = new PageImpl<>(posts, pageable, 11);
        when(postRepository.findAll(pageable)).thenReturn(postPage);

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
            .andExpect(model().attribute("posts", posts))
            .andExpect(model().attribute("postPage", postPage))
            .andExpect(content().string(containsString("共 <span>11</span> 篇")))
            .andExpect(content().string(containsString("第 1 / 2 页")))
            .andExpect(content().string(containsString("aria-disabled=\"true\">上一页</span>")))
            .andExpect(content().string(not(containsString("rel=\"prev\""))))
            .andExpect(content().string(containsString("href=\"/admin/posts?page=1\"")))
            .andReturn();

        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] articles = html.split("<article class=\"admin-item\">");
        assertThat(articles).hasSize(11);
        String publishedArticle = articles[1].split("</article>")[0];
        String draftArticle = articles[2].split("</article>")[0];
        assertThat(publishedArticle).contains("已发布", "href=\"/posts/", "编辑", "删除");
        assertThat(draftArticle).contains("草稿", "编辑", "删除").doesNotContain("href=\"/posts/", "查看");

        mockMvc.perform(get("/admin/posts").param("page", "0").param("size", "1").param("sort", "id,asc"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("postPage", postPage));
        verify(postRepository, times(2)).findAll(pageable);
        verify(postRepository, never()).findByStatus(any(PostStatus.class), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void adminPostsHidesPaginationForEmptyOrSinglePage(int count) throws Exception {
        var pageable = PageRequest.of(0, 10, POST_SORT);
        var posts = IntStream.rangeClosed(1, count)
            .mapToObj(i -> new Post("后台文章 " + i, "摘要", "正文",
                i % 2 == 0 ? PostStatus.DRAFT : PostStatus.PUBLISHED))
            .toList();
        var postPage = new PageImpl<>(posts, pageable, count);
        when(postRepository.findAll(pageable)).thenReturn(postPage);

        var result = mockMvc.perform(get("/admin/posts"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/posts"))
            .andExpect(model().attribute("posts", posts))
            .andExpect(model().attribute("postPage", postPage))
            .andExpect(content().string(containsString("共 <span>" + count + "</span> 篇")))
            .andExpect(content().string(not(containsString("class=\"pagination\""))));
        if (count == 0) {
            result.andExpect(content().string(containsString("暂无文章")));
            var nextPage = PageRequest.of(1, 10, POST_SORT);
            when(postRepository.findAll(nextPage)).thenReturn(Page.empty(nextPage));
            mockMvc.perform(get("/admin/posts").param("page", "1"))
                .andExpect(status().isNotFound());
        } else {
            result.andExpect(content().string(not(containsString("暂无文章"))));
        }
    }

    @ParameterizedTest
    @CsvSource({"11, 1, false", "21, 10, true"})
    void adminPostsRendersSecondPageWithCorrectNavigation(long total, int count, boolean hasNext) throws Exception {
        var pageable = PageRequest.of(1, 10, POST_SORT);
        var posts = IntStream.rangeClosed(1, count)
            .mapToObj(i -> new Post("后台第二页 " + i, "摘要", "正文",
                i % 2 == 0 ? PostStatus.PUBLISHED : PostStatus.DRAFT))
            .toList();
        var postPage = new PageImpl<>(posts, pageable, total);
        when(postRepository.findAll(pageable)).thenReturn(postPage);

        var result = mockMvc.perform(get("/admin/posts").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/posts"))
            .andExpect(model().attribute("posts", posts))
            .andExpect(model().attribute("postPage", postPage))
            .andExpect(content().string(containsString("草稿")))
            .andExpect(content().string(containsString("共 <span>" + total + "</span> 篇")))
            .andExpect(content().string(containsString("href=\"/admin/posts?page=0\"")))
            .andExpect(content().string(containsString(hasNext ? "第 2 / 3 页" : "第 2 / 2 页")));
        if (hasNext) {
            result.andExpect(content().string(containsString("已发布")))
                .andExpect(content().string(containsString("href=\"/admin/posts?page=2\"")));
        } else {
            result.andExpect(content().string(containsString("aria-disabled=\"true\">下一页</span>")))
                .andExpect(content().string(not(containsString("rel=\"next\""))));
        }
        assertThat(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
            .split("<article class=\"admin-item\">")).hasSize(count + 1);
        verify(postRepository).findAll(pageable);
    }

    @Test
    void adminPostsRejectsNegativePagesAndOffsetsBeyondJpaLimit() throws Exception {
        for (String page : List.of("-1", "2147483647")) {
            mockMvc.perform(get("/admin/posts").param("page", page))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(postRepository);
    }

    @Test
    void adminPostsRejectsInvalidPageBindings() throws Exception {
        for (String page : List.of("abc", "1.5", "2147483648")) {
            mockMvc.perform(get("/admin/posts").param("page", page))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(postRepository);
    }

    @Test
    void adminPostsReturnsNotFoundForPageBeyondLast() throws Exception {
        var pageable = PageRequest.of(999, 10, POST_SORT);
        when(postRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 11));

        mockMvc.perform(get("/admin/posts").param("page", "999"))
            .andExpect(status().isNotFound());
        verify(postRepository).findAll(pageable);
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
