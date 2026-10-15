package com.example.blog.web;

import com.example.blog.domain.Post;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
        Post p1 = new Post("你好，世界", "第一篇摘要", "第一篇内容");
        Post p2 = new Post("第二篇", "第二篇摘要", "第二篇内容");
        when(postRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"))
            .andExpect(content().string(containsString("Blog")))
            .andExpect(content().string(containsString("你好，世界")))
            .andExpect(content().string(containsString("第二篇")))
            .andExpect(content().string(containsString("最新文章")));
    }

    @Test
    void indexRendersEmptyStateWhenNoPosts() throws Exception {
        when(postRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of());

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"))
            .andExpect(content().string(containsString("暂时还没有文章")))
            .andExpect(content().string(containsString("还没有任何文章")));
    }

    @Test
    void indexIncludesAdminLink() throws Exception {
        when(postRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of());

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/admin/posts")))
            .andExpect(content().string(containsString("后台管理")));
    }

    @Test
    void postRendersWhenPresent() throws Exception {
        Post post = new Post("文章详情标题", "文章详情摘要", "这里是完整文章正文。");
        when(postRepository.findById(42L)).thenReturn(Optional.of(post));

        mockMvc.perform(get("/posts/{id}", 42L))
            .andExpect(status().isOk())
            .andExpect(view().name("post"))
            .andExpect(model().attribute("post", post))
            .andExpect(content().string(containsString("文章详情标题")))
            .andExpect(content().string(containsString("这里是完整文章正文。")));
    }

    @Test
    void postReturnsNotFoundWhenMissing() throws Exception {
        when(postRepository.findById(999999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/posts/{id}", 999999L))
            .andExpect(status().isNotFound());
    }
}
