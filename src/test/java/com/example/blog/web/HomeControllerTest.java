package com.example.blog.web;

import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import com.example.blog.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostRepository postRepository;

    @Test
    void indexRendersPostsWhenPresent() throws Exception {
        Post p1 = new Post("你好，世界", "第一篇摘要", "第一篇内容", PostStatus.PUBLISHED);
        Post p2 = new Post("第二篇", "第二篇摘要", "第二篇内容", PostStatus.PUBLISHED);
        Post draft = new Post("未公开标题", "未公开摘要", "未公开正文", PostStatus.DRAFT);
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        when(postRepository.findAll(sort)).thenReturn(List.of(p1, draft, p2));
        when(postRepository.findByStatus(PostStatus.PUBLISHED, sort)).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"))
            .andExpect(content().string(containsString("Blog")))
            .andExpect(content().string(containsString("你好，世界")))
            .andExpect(content().string(containsString("第二篇")))
            .andExpect(content().string(containsString("最新文章")))
            .andExpect(content().string(not(containsString("未公开"))))
            .andExpect(model().attribute("posts", List.of(p1, p2)))
            .andExpect(model().attribute("hasPosts", true))
            .andExpect(model().attribute("postCount", 2));

        verify(postRepository).findByStatus(PostStatus.PUBLISHED, sort);
        verify(postRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void indexRendersEmptyStateWhenOnlyDraftsExist() throws Exception {
        Post draft = new Post("未公开标题", "未公开摘要", "未公开正文", PostStatus.DRAFT);
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        when(postRepository.findAll(sort)).thenReturn(List.of(draft));
        when(postRepository.findByStatus(PostStatus.PUBLISHED, sort)).thenReturn(List.of());

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"))
            .andExpect(content().string(containsString("暂无已发布文章")))
            .andExpect(content().string(not(containsString("未公开"))))
            .andExpect(model().attribute("hasPosts", false))
            .andExpect(model().attribute("postCount", 0));

        verify(postRepository).findByStatus(PostStatus.PUBLISHED, sort);
        verify(postRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void indexIncludesAdminLink() throws Exception {
        when(postRepository.findByStatus(PostStatus.PUBLISHED, Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of());

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/admin/posts")))
            .andExpect(content().string(containsString("后台管理")));
    }

    @Test
    void postRendersWhenPresent() throws Exception {
        Post post = new Post("文章详情标题", "文章详情摘要", "这里是完整文章正文。", PostStatus.PUBLISHED);
        when(postRepository.findByIdAndStatus(42L, PostStatus.PUBLISHED)).thenReturn(Optional.of(post));

        mockMvc.perform(get("/posts/{id}", 42L))
            .andExpect(status().isOk())
            .andExpect(view().name("post"))
            .andExpect(model().attribute("post", post))
            .andExpect(content().string(containsString("文章详情标题")))
            .andExpect(content().string(containsString("这里是完整文章正文。")));
    }

    @Test
    void postReturnsNotFoundWhenMissing() throws Exception {
        when(postRepository.findByIdAndStatus(999999L, PostStatus.PUBLISHED)).thenReturn(Optional.empty());

        mockMvc.perform(get("/posts/{id}", 999999L))
            .andExpect(status().isNotFound());
    }

    @Test
    void draftDetailReturnsNotFoundWithoutLeakingContent() throws Exception {
        Post draft = new Post("未公开标题", "未公开摘要", "未公开正文", PostStatus.DRAFT);
        when(postRepository.findById(42L)).thenReturn(Optional.of(draft));
        when(postRepository.findByIdAndStatus(42L, PostStatus.PUBLISHED)).thenReturn(Optional.empty());

        mockMvc.perform(get("/posts/42"))
            .andExpect(status().isNotFound())
            .andExpect(content().string(not(containsString("未公开"))))
            .andExpect(content().string(not(containsString("草稿"))));

        verify(postRepository).findByIdAndStatus(42L, PostStatus.PUBLISHED);
        verify(postRepository, never()).findById(42L);
    }
}
