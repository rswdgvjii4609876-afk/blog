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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
        Post p1 = new Post("后台标题 1", "后台摘要 1", "后台正文 1");
        Post p2 = new Post("后台标题 2", "后台摘要 2", "后台正文 2");
        when(postRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/admin/posts"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/posts"))
            .andExpect(content().string(containsString("后台文章管理")))
            .andExpect(content().string(containsString("后台标题 1")))
            .andExpect(content().string(containsString("/admin/posts/new")))
            .andExpect(content().string(containsString("/posts/")));
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
                .param("content", "测试正文"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/posts/42"));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post submitted = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("测试标题", submitted.getTitle());
        org.junit.jupiter.api.Assertions.assertEquals("测试摘要", submitted.getSummary());
        org.junit.jupiter.api.Assertions.assertEquals("测试正文", submitted.getContent());
    }
}
