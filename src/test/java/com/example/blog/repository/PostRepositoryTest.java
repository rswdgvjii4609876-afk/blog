package com.example.blog.repository;

import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void statusPersistsByNameAndUpdatesPreserveCreationTime() {
        Post draft = postRepository.save(new Post("草稿", "摘要", "正文", PostStatus.DRAFT));
        Post published = postRepository.save(new Post("已发布", "摘要", "正文", PostStatus.PUBLISHED));
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of("DRAFT", "PUBLISHED"),
            entityManager.createNativeQuery("select status from posts order by id", String.class).getResultList());
        Post reloaded = postRepository.findById(draft.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(postRepository.findById(published.getId()).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
        Instant createdAt = reloaded.getCreatedAt();
        assertThat(createdAt).isNotNull();

        reloaded.update("更新草稿", "更新摘要", "更新正文", PostStatus.PUBLISHED);
        postRepository.saveAndFlush(reloaded);
        entityManager.clear();

        Post updated = postRepository.findById(draft.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void publishedPagesExcludeDraftsAndSortByCreationTimeThenIdDescending() {
        Post newest = savePostAt("最新文章", PostStatus.PUBLISHED, Instant.parse("2026-01-03T00:00:00Z"));
        Post oldest = savePostAt("最早文章", PostStatus.PUBLISHED, Instant.parse("2026-01-01T00:00:00Z"));
        Instant sharedTime = Instant.parse("2026-01-02T00:00:00Z");
        Post firstAtSameTime = savePostAt("同时间第一篇", PostStatus.PUBLISHED, sharedTime);
        Post draft = savePostAt("更新的草稿", PostStatus.DRAFT, Instant.parse("2026-01-04T00:00:00Z"));
        Post secondAtSameTime = savePostAt("同时间第二篇", PostStatus.PUBLISHED, sharedTime);
        entityManager.clear();

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        var firstPage = postRepository.findByStatus(PostStatus.PUBLISHED, PageRequest.of(0, 2, sort));
        var secondPage = postRepository.findByStatus(PostStatus.PUBLISHED, PageRequest.of(1, 2, sort));

        assertThat(firstPage.getTotalElements()).isEqualTo(4L);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent())
            .extracting(Post::getId)
            .containsExactly(newest.getId(), secondAtSameTime.getId());
        assertThat(secondPage.getTotalElements()).isEqualTo(4L);
        assertThat(secondPage.getContent())
            .extracting(Post::getId)
            .containsExactly(firstAtSameTime.getId(), oldest.getId());
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.hasPrevious()).isTrue();

        var adminPage = postRepository.findAll(PageRequest.of(0, 2, sort));
        assertThat(adminPage.getTotalElements()).isEqualTo(5L);
        assertThat(adminPage.getContent())
            .extracting(Post::getId)
            .containsExactly(draft.getId(), newest.getId());
    }

    @Test
    void publicDetailQueryFindsPublishedAndExcludesDrafts() {
        Post published = postRepository.save(new Post("已发布", "摘要", "正文", PostStatus.PUBLISHED));
        Post draft = postRepository.save(new Post("草稿", "摘要", "正文", PostStatus.DRAFT));
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findByIdAndStatus(published.getId(), PostStatus.PUBLISHED))
            .hasValueSatisfying(post -> assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED));
        assertThat(postRepository.findByIdAndStatus(draft.getId(), PostStatus.PUBLISHED)).isEmpty();
        assertThat(postRepository.findByIdAndStatus(Long.MAX_VALUE, PostStatus.PUBLISHED)).isEmpty();
    }

    @Test
    void searchMatchesTitleOrSummaryIgnoringCaseAndLimitsPublicResultsToPublished() {
        Post titleMatch = new Post("Spring 入门", "概览", "正文", PostStatus.PUBLISHED);
        Post summaryMatch = new Post("学习笔记", "了解 SPRING Boot", "正文", PostStatus.PUBLISHED);
        Post bothMatch = new Post("spring 实践", "spring 摘要", "正文", PostStatus.PUBLISHED);
        Post draftTitle = new Post("Spring 草稿", "概览", "正文", PostStatus.DRAFT);
        Post draftSummary = new Post("私有备忘", "Spring 草稿摘要", "正文", PostStatus.DRAFT);
        postRepository.saveAll(List.of(titleMatch, summaryMatch, bothMatch, draftTitle, draftSummary,
            new Post("正文记录", "其他摘要", "spring 仅在正文中", PostStatus.PUBLISHED),
            new Post("Java", "其他内容", "正文", PostStatus.PUBLISHED)));
        entityManager.flush();
        entityManager.clear();
        var pageable = PageRequest.of(0, 10);

        var publicPage = postRepository.searchByStatus(PostStatus.PUBLISHED, "sPrInG", pageable);
        assertThat(publicPage.getTotalElements()).isEqualTo(3L);
        assertThat(publicPage.getContent()).extracting(Post::getId)
            .containsExactlyInAnyOrder(titleMatch.getId(), summaryMatch.getId(), bothMatch.getId());

        var adminPage = postRepository.findByTitleContainingIgnoreCaseOrSummaryContainingIgnoreCase(
            "sPrInG", "sPrInG", pageable);
        assertThat(adminPage.getTotalElements()).isEqualTo(5L);
        assertThat(adminPage.getContent()).extracting(Post::getId)
            .containsExactlyInAnyOrder(titleMatch.getId(), summaryMatch.getId(), bothMatch.getId(),
                draftTitle.getId(), draftSummary.getId());
    }

    @Test
    void searchPagesKeepFilteredTotalsAndStableCreationTimeAndIdOrdering() {
        Post newest = savePostAt("spring 最新", PostStatus.PUBLISHED, Instant.parse("2026-01-03T00:00:00Z"));
        Post oldest = savePostAt("spring 最早", PostStatus.PUBLISHED, Instant.parse("2026-01-01T00:00:00Z"));
        Instant sharedTime = Instant.parse("2026-01-02T00:00:00Z");
        Post firstAtSameTime = savePostAt("spring 同时间一", PostStatus.PUBLISHED, sharedTime);
        Post draft = savePostAt("spring 草稿", PostStatus.DRAFT, Instant.parse("2026-01-04T00:00:00Z"));
        Post secondAtSameTime = savePostAt("spring 同时间二", PostStatus.PUBLISHED, sharedTime);
        savePostAt("不匹配的更新文章", PostStatus.PUBLISHED, Instant.parse("2026-01-05T00:00:00Z"));
        entityManager.clear();
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        var firstPage = PageRequest.of(0, 2, sort);
        var secondPage = PageRequest.of(1, 2, sort);

        var publicFirst = postRepository.searchByStatus(PostStatus.PUBLISHED, "spring", firstPage);
        var publicSecond = postRepository.searchByStatus(PostStatus.PUBLISHED, "spring", secondPage);
        assertThat(publicFirst.getTotalElements()).isEqualTo(4L);
        assertThat(publicFirst.getTotalPages()).isEqualTo(2);
        assertThat(publicFirst.getContent()).extracting(Post::getId)
            .containsExactly(newest.getId(), secondAtSameTime.getId());
        assertThat(publicSecond.getTotalElements()).isEqualTo(4L);
        assertThat(publicSecond.getContent()).extracting(Post::getId)
            .containsExactly(firstAtSameTime.getId(), oldest.getId());

        var adminFirst = postRepository.findByTitleContainingIgnoreCaseOrSummaryContainingIgnoreCase(
            "spring", "spring", firstPage);
        var adminSecond = postRepository.findByTitleContainingIgnoreCaseOrSummaryContainingIgnoreCase(
            "spring", "spring", secondPage);
        var adminLast = postRepository.findByTitleContainingIgnoreCaseOrSummaryContainingIgnoreCase(
            "spring", "spring", PageRequest.of(2, 2, sort));
        assertThat(adminFirst.getTotalElements()).isEqualTo(5L);
        assertThat(adminFirst.getTotalPages()).isEqualTo(3);
        assertThat(adminFirst.getContent()).extracting(Post::getId)
            .containsExactly(draft.getId(), newest.getId());
        assertThat(adminSecond.getContent()).extracting(Post::getId)
            .containsExactly(secondAtSameTime.getId(), firstAtSameTime.getId());
        assertThat(adminLast.getTotalElements()).isEqualTo(5L);
        assertThat(adminLast.getContent()).extracting(Post::getId).containsExactly(oldest.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\", "%\u0027 or 1=1 --"})
    void searchTreatsWildcardsEscapeCharacterAndSqlLikeInputAsLiteralText(String keyword) {
        Post titleMatch = new Post("标题" + keyword + "内容", "普通摘要", "正文", PostStatus.PUBLISHED);
        Post summaryMatch = new Post("普通标题", "摘要" + keyword + "内容", "正文", PostStatus.PUBLISHED);
        Post draft = new Post("草稿" + keyword + "内容", "草稿" + keyword + "摘要", "正文", PostStatus.DRAFT);
        postRepository.saveAll(List.of(titleMatch, summaryMatch, draft,
            new Post("普通标题", "普通摘要", "正文" + keyword, PostStatus.PUBLISHED)));
        entityManager.flush();
        entityManager.clear();
        var pageable = PageRequest.of(0, 10);

        var publicPage = postRepository.searchByStatus(PostStatus.PUBLISHED, keyword, pageable);
        assertThat(publicPage.getTotalElements()).isEqualTo(2L);
        assertThat(publicPage.getContent()).extracting(Post::getId)
            .containsExactlyInAnyOrder(titleMatch.getId(), summaryMatch.getId());

        var adminPage = postRepository.findByTitleContainingIgnoreCaseOrSummaryContainingIgnoreCase(
            keyword, keyword, pageable);
        assertThat(adminPage.getTotalElements()).isEqualTo(3L);
        assertThat(adminPage.getContent()).extracting(Post::getId)
            .containsExactlyInAnyOrder(titleMatch.getId(), summaryMatch.getId(), draft.getId());
    }

    private Post savePostAt(String title, PostStatus status, Instant createdAt) {
        Post post = postRepository.saveAndFlush(new Post(title, "摘要", "正文", status));
        entityManager.createNativeQuery("update posts set created_at = :createdAt where id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", post.getId())
            .executeUpdate();
        return post;
    }
}
