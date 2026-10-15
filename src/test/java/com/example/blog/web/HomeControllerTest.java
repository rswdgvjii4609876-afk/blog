package com.example.blog.web;

import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import com.example.blog.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
class HomeControllerTest {

    private static final Sort POST_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostRepository postRepository;

    @Test
    void indexRendersPostsWhenPresent() throws Exception {
        var pageable = PageRequest.of(0, 5, POST_SORT);
        var posts = IntStream.rangeClosed(1, 5)
            .mapToObj(i -> new Post("公开文章 " + i, "摘要", "正文", PostStatus.PUBLISHED))
            .toList();
        var postPage = new PageImpl<>(posts, pageable, 6);
        when(postRepository.findByStatus(PostStatus.PUBLISHED, pageable)).thenReturn(postPage);

        var result = mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"))
            .andExpect(content().string(containsString("Blog")))
            .andExpect(content().string(containsString("公开文章 1")))
            .andExpect(content().string(containsString("公开文章 5")))
            .andExpect(content().string(containsString("最新文章")))
            .andExpect(content().string(not(containsString("未公开"))))
            .andExpect(model().attribute("posts", posts))
            .andExpect(model().attribute("postPage", postPage))
            .andExpect(content().string(containsString("共 <span>6</span> 篇")))
            .andExpect(content().string(containsString("第 1 / 2 页")))
            .andExpect(content().string(containsString("aria-disabled=\"true\">上一页</span>")))
            .andExpect(content().string(not(containsString("rel=\"prev\""))))
            .andExpect(content().string(containsString("href=\"/?page=1\"")))
            .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)
            .split("<article class=\"article-row\">")).hasSize(6);
        mockMvc.perform(get("/").param("page", "0").param("size", "1").param("sort", "id,asc"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("postPage", postPage));

        verify(postRepository, times(2)).findByStatus(PostStatus.PUBLISHED, pageable);
        verify(postRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void indexRendersEmptyStateWhenOnlyDraftsExist() throws Exception {
        Post draft = new Post("未公开标题", "未公开摘要", "未公开正文", PostStatus.DRAFT);
        var pageable = PageRequest.of(0, 5, POST_SORT);
        var nextPage = PageRequest.of(1, 5, POST_SORT);
        when(postRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(draft), pageable, 1));
        when(postRepository.findByStatus(PostStatus.PUBLISHED, pageable)).thenReturn(Page.empty(pageable));
        when(postRepository.findByStatus(PostStatus.PUBLISHED, nextPage)).thenReturn(Page.empty(nextPage));

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"))
            .andExpect(content().string(containsString("暂无已发布文章")))
            .andExpect(content().string(not(containsString("未公开"))))
            .andExpect(content().string(containsString("共 <span>0</span> 篇")))
            .andExpect(content().string(not(containsString("class=\"pagination\""))))
            .andExpect(model().attribute("posts", List.of()));

        mockMvc.perform(get("/").param("page", "1"))
            .andExpect(status().isNotFound());

        verify(postRepository).findByStatus(PostStatus.PUBLISHED, pageable);
        verify(postRepository).findByStatus(PostStatus.PUBLISHED, nextPage);
        verify(postRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void indexIncludesAdminLink() throws Exception {
        var pageable = PageRequest.of(0, 5, POST_SORT);
        var post = new Post("单篇文章", "摘要", "正文", PostStatus.PUBLISHED);
        when(postRepository.findByStatus(PostStatus.PUBLISHED, pageable))
            .thenReturn(new PageImpl<>(List.of(post), pageable, 1));

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/admin/posts")))
            .andExpect(content().string(containsString("后台管理")))
            .andExpect(content().string(containsString("单篇文章")))
            .andExpect(content().string(not(containsString("class=\"pagination\""))));
    }

    @ParameterizedTest
    @CsvSource({"6, 1, false", "11, 5, true"})
    void indexRendersSecondPageWithCorrectNavigation(long total, int count, boolean hasNext) throws Exception {
        var pageable = PageRequest.of(1, 5, POST_SORT);
        var posts = IntStream.rangeClosed(1, count)
            .mapToObj(i -> new Post("第二页文章 " + i, "摘要", "正文", PostStatus.PUBLISHED))
            .toList();
        var postPage = new PageImpl<>(posts, pageable, total);
        when(postRepository.findByStatus(PostStatus.PUBLISHED, pageable)).thenReturn(postPage);

        var result = mockMvc.perform(get("/").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"))
            .andExpect(model().attribute("posts", posts))
            .andExpect(model().attribute("postPage", postPage))
            .andExpect(content().string(containsString("共 <span>" + total + "</span> 篇")))
            .andExpect(content().string(containsString("href=\"/?page=0\"")))
            .andExpect(content().string(containsString(hasNext ? "第 2 / 3 页" : "第 2 / 2 页")));
        if (hasNext) {
            result.andExpect(content().string(containsString("href=\"/?page=2\"")));
        } else {
            result.andExpect(content().string(containsString("aria-disabled=\"true\">下一页</span>")))
                .andExpect(content().string(not(containsString("rel=\"next\""))));
        }
        assertThat(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
            .split("<article class=\"article-row\">")).hasSize(count + 1);
        verify(postRepository).findByStatus(PostStatus.PUBLISHED, pageable);
    }

    @Test
    void indexRejectsNegativePagesAndOffsetsBeyondJpaLimit() throws Exception {
        for (String page : List.of("-1", "2147483647")) {
            mockMvc.perform(get("/").param("page", page))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(postRepository);
    }

    @Test
    void indexRejectsInvalidPageBindings() throws Exception {
        for (String page : List.of("abc", "1.5", "2147483648")) {
            mockMvc.perform(get("/").param("page", page))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(postRepository);
    }

    @Test
    void indexReturnsNotFoundForPageBeyondLast() throws Exception {
        var pageable = PageRequest.of(999, 5, POST_SORT);
        when(postRepository.findByStatus(PostStatus.PUBLISHED, pageable))
            .thenReturn(new PageImpl<>(List.of(), pageable, 6));

        mockMvc.perform(get("/").param("page", "999"))
            .andExpect(status().isNotFound());
        verify(postRepository).findByStatus(PostStatus.PUBLISHED, pageable);
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
