package com.example.blog.web;

import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import com.example.blog.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import java.net.URLDecoder;
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

@org.springframework.context.annotation.Import({com.example.blog.service.MarkdownService.class, com.example.blog.config.SecurityConfig.class})
@WebMvcTest(HomeController.class)
class HomeControllerTest {

    private static final Sort POST_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostRepository postRepository;

    @MockitoBean
    private com.example.blog.service.ReaderFeedbackService feedback;

    @MockitoBean
    private com.example.blog.service.CommunityService community;

    @Test
    void moodAppearsOnlyOnFirstUnfilteredHomePage() throws Exception {
        var home = new com.example.blog.service.CommunityService.Home(
            new com.example.blog.service.CommunityService.Settings("欢迎", 0), List.of(), null, 0,
            null, List.of(), 0, 0, 0);
        when(community.home(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(home);
        var post = new Post("主题", "摘要", "正文", PostStatus.PUBLISHED);
        var first = PageRequest.of(0, 5, POST_SORT);
        var next = PageRequest.of(1, 5, POST_SORT);
        when(postRepository.findByStatus(PostStatus.PUBLISHED, first)).thenReturn(new PageImpl<>(List.of(post), first, 6));
        when(postRepository.findByStatus(PostStatus.PUBLISHED, next)).thenReturn(new PageImpl<>(List.of(post), next, 6));
        when(postRepository.searchByStatus(PostStatus.PUBLISHED, "主题", first)).thenReturn(new PageImpl<>(List.of(post), first, 1));
        mockMvc.perform(get("/")).andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"mood\"")));
        mockMvc.perform(get("/").param("page", "1")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"mood\""))));
        mockMvc.perform(get("/").param("q", "主题")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"mood\""))));
    }

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
            .andExpect(content().string(containsString("知行")))
            .andExpect(model().attribute("q", ""))
            .andExpect(content().string(containsString("role=\"search\"")))
            .andExpect(content().string(containsString("type=\"search\"")))
            .andExpect(content().string(containsString("公开文章 1")))
            .andExpect(content().string(containsString("公开文章 5")))
            .andExpect(content().string(containsString("最新文章")))
            .andExpect(content().string(not(containsString("未公开"))))
            .andExpect(model().attribute("posts", posts))
            .andExpect(model().attribute("postPage", postPage))
            .andExpect(content().string(containsString("第 1 / 2 页")))
            .andExpect(content().string(containsString("aria-disabled=\"true\">上一页</span>")))
            .andExpect(content().string(not(containsString("rel=\"prev\""))))
            .andExpect(content().string(containsString("href=\"/?page=1\"")))
            .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)
            .split("<article class=\"article-row")).hasSize(6);
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
            .andExpect(content().string(not(containsString("class=\"pagination\""))))
            .andExpect(model().attribute("posts", List.of()));

        mockMvc.perform(get("/").param("page", "1"))
            .andExpect(status().isNotFound());

        verify(postRepository).findByStatus(PostStatus.PUBLISHED, pageable);
        verify(postRepository).findByStatus(PostStatus.PUBLISHED, nextPage);
        verify(postRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void indexOffersIdentitySwitchInsteadOfManagementLinks() throws Exception {
        var pageable = PageRequest.of(0, 5, POST_SORT);
        var post = new Post("单篇文章", "摘要", "正文", PostStatus.PUBLISHED);
        when(postRepository.findByStatus(PostStatus.PUBLISHED, pageable))
            .thenReturn(new PageImpl<>(List.of(post), pageable, 1));

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("href=\"/admin/"))))
            .andExpect(content().string(containsString("身份切换")))
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
            .andExpect(content().string(containsString("href=\"/?page=0\"")))
            .andExpect(content().string(containsString(hasNext ? "第 2 / 3 页" : "第 2 / 2 页")));
        if (hasNext) {
            result.andExpect(content().string(containsString("href=\"/?page=2\"")));
        } else {
            result.andExpect(content().string(containsString("aria-disabled=\"true\">下一页</span>")))
                .andExpect(content().string(not(containsString("rel=\"next\""))));
        }
        assertThat(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
            .split("<article class=\"article-row")).hasSize(count + 1);
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

    @ParameterizedTest
    @ValueSource(strings = {"", " \t ", "\u2003"})
    void blankSearchUsesNormalPaginationWithoutEmptyKeywordLinks(String q) throws Exception {
        var pageable = PageRequest.of(1, 5, POST_SORT);
        var posts = List.of(new Post("普通文章", "摘要", "正文", PostStatus.PUBLISHED));
        when(postRepository.findByStatus(PostStatus.PUBLISHED, pageable))
            .thenReturn(new PageImpl<>(posts, pageable, 6));

        mockMvc.perform(get("/").param("q", q).param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("q", ""))
            .andExpect(content().string(containsString("往期文章")))
            .andExpect(content().string(containsString("href=\"/?page=0\"")))
            .andExpect(content().string(not(containsString("?q="))))
            .andExpect(content().string(not(containsString("清除搜索"))));

        verify(postRepository).findByStatus(PostStatus.PUBLISHED, pageable);
        verify(postRepository, never()).searchByStatus(any(PostStatus.class), any(String.class), any(Pageable.class));
    }

    @Test
    void searchStripsKeywordAndAlwaysQueriesPublishedWithMatchingTotal() throws Exception {
        var pageable = PageRequest.of(0, 5, POST_SORT);
        var posts = IntStream.rangeClosed(1, 5)
            .mapToObj(i -> new Post("Spring 文章 " + i, "摘要", "正文", PostStatus.PUBLISHED))
            .toList();
        var postPage = new PageImpl<>(posts, pageable, 6);
        when(postRepository.searchByStatus(PostStatus.PUBLISHED, "Spring", pageable)).thenReturn(postPage);

        mockMvc.perform(get("/").param("q", "\u2003 Spring \t").param("status", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"))
            .andExpect(model().attribute("q", "Spring"))
            .andExpect(model().attribute("posts", posts))
            .andExpect(model().attribute("postPage", postPage))
            .andExpect(content().string(containsString("value=\"Spring\"")))
            .andExpect(content().string(containsString("搜索结果")))
            .andExpect(content().string(containsString("清除搜索")))
            .andExpect(content().string(containsString("href=\"/?q=Spring&amp;page=1\"")))
            .andExpect(content().string(not(containsString("name=\"page\""))));

        verify(postRepository).searchByStatus(PostStatus.PUBLISHED, "Spring", pageable);
        verify(postRepository, never()).findByStatus(any(PostStatus.class), any(Pageable.class));
        verify(postRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void searchSecondPageKeepsKeywordAndFixedPageSizeAndSort() throws Exception {
        var pageable = PageRequest.of(1, 5, POST_SORT);
        var posts = IntStream.rangeClosed(1, 5)
            .mapToObj(i -> new Post("搜索第二页 " + i, "摘要", "正文", PostStatus.PUBLISHED))
            .toList();
        var postPage = new PageImpl<>(posts, pageable, 11);
        when(postRepository.searchByStatus(PostStatus.PUBLISHED, "spring", pageable)).thenReturn(postPage);

        mockMvc.perform(get("/").param("q", "spring").param("page", "1")
                .param("size", "1").param("sort", "id,asc"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("posts", posts))
            .andExpect(model().attribute("postPage", postPage))
            .andExpect(content().string(containsString("第 2 / 3 页")))
            .andExpect(content().string(containsString("href=\"/?q=spring&amp;page=0\"")))
            .andExpect(content().string(containsString("href=\"/?q=spring&amp;page=2\"")))
            .andExpect(content().string(not(containsString("name=\"page\""))));

        verify(postRepository).searchByStatus(PostStatus.PUBLISHED, "spring", pageable);
    }

    @Test
    void searchEmptyFirstPageIsOkAndPagesBeyondFilteredResultsAreNotFound() throws Exception {
        var first = PageRequest.of(0, 5, POST_SORT);
        var second = PageRequest.of(1, 5, POST_SORT);
        when(postRepository.searchByStatus(PostStatus.PUBLISHED, "missing", first)).thenReturn(Page.empty(first));
        when(postRepository.searchByStatus(PostStatus.PUBLISHED, "missing", second)).thenReturn(Page.empty(second));

        mockMvc.perform(get("/").param("q", "missing"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("q", "missing"))
            .andExpect(content().string(containsString("没有找到与“missing”匹配的文章")))
            .andExpect(content().string(containsString("试试其他关键词，或清除搜索查看全部文章。")))
            .andExpect(content().string(not(containsString("暂无已发布文章"))))
            .andExpect(content().string(not(containsString("class=\"pagination\""))));
        mockMvc.perform(get("/").param("q", "missing").param("page", "1"))
            .andExpect(status().isNotFound());

        var beyond = PageRequest.of(5, 5, POST_SORT);
        when(postRepository.searchByStatus(PostStatus.PUBLISHED, "spring", beyond))
            .thenReturn(new PageImpl<>(List.of(), beyond, 1));
        mockMvc.perform(get("/").param("q", "spring").param("page", "5"))
            .andExpect(status().isNotFound());
    }

    @Test
    void searchValidatesKeywordLengthAfterStripping() throws Exception {
        mockMvc.perform(get("/").param("q", "a".repeat(101)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(postRepository);

        String keyword = "a".repeat(100);
        var pageable = PageRequest.of(0, 5, POST_SORT);
        when(postRepository.searchByStatus(PostStatus.PUBLISHED, keyword, pageable)).thenReturn(Page.empty(pageable));
        mockMvc.perform(get("/").param("q", "\u2003 " + keyword + " \t"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("q", keyword));
        verify(postRepository).searchByStatus(PostStatus.PUBLISHED, keyword, pageable);
    }

    @Test
    void searchEscapesHtmlAndPreservesSpecialCharactersInPagination() throws Exception {
        String keyword = "\"><script>alert(1)</script>&中文%_\\+";
        var pageable = PageRequest.of(0, 5, POST_SORT);
        var posts = IntStream.rangeClosed(1, 5)
            .mapToObj(i -> new Post("匹配文章 " + i, "摘要", "正文", PostStatus.PUBLISHED))
            .toList();
        when(postRepository.searchByStatus(PostStatus.PUBLISHED, keyword, pageable))
            .thenReturn(new PageImpl<>(posts, pageable, 6));

        var result = mockMvc.perform(get("/").param("q", keyword))
            .andExpect(status().isOk())
            .andExpect(model().attribute("q", keyword))
            .andExpect(content().string(containsString("value=\"&quot;&gt;&lt;script&gt;")))
            .andExpect(content().string(not(containsString("<script>"))))
            .andReturn();
        var nextLink = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .selectFirst("a[rel=next]");
        assertThat(nextLink).isNotNull();
        assertThat(URLDecoder.decode(nextLink.attr("href"), StandardCharsets.UTF_8))
            .isEqualTo("/?q=" + keyword + "&page=1");

        when(postRepository.searchByStatus(PostStatus.PUBLISHED, keyword, pageable)).thenReturn(Page.empty(pageable));
        mockMvc.perform(get("/").param("q", keyword))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("没有找到与“")))
            .andExpect(content().string(containsString("&lt;script&gt;")))
            .andExpect(content().string(not(containsString("<script>"))));
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
