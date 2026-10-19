package com.example.blog.service;

import com.example.blog.domain.*;
import com.example.blog.repository.*;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PostTransactionTest {
    @Autowired PostService service;
    @Autowired PostRepository posts;
    @Autowired PostRevisionRepository revisions;
    @Autowired EntityManagerFactory factory;
    @Autowired ReaderFeedbackService feedback;
    @Autowired CommunityService community;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void concurrentMoodAndPollUpdatesKeepOneRowPerBrowser() throws Exception {
        String reader = java.util.UUID.randomUUID().toString();
        community.createPoll("并发互动", "甲\n乙");
        long poll = community.home(reader, 0).poll().id();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); community.mood(reader, CommunityService.Mood.CALM); community.vote(poll, reader, 0); return true; });
            var second = executor.submit(() -> { start.await(); community.mood(reader, CommunityService.Mood.HAPPY); community.vote(poll, reader, 1); return true; });
            start.countDown();
            assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from mood_entries where reader_id=?", Long.class, reader)).isEqualTo(1);
            assertThat(community.poll(poll, reader).votes()).isEqualTo(1);
        } finally {
            jdbc.update("delete from poll_votes where poll_id=?", poll);
            jdbc.update("delete from reader_polls where id=?", poll);
            jdbc.update("delete from mood_entries where reader_id=?", reader);
        }
    }

    @Test
    void simultaneousFirstRatingsFromSameBrowserCreateExactlyOneVote() throws Exception {
        var post = service.create("并发评分", "摘要", "正文", PostStatus.PUBLISHED, null);
        var reader = java.util.UUID.randomUUID().toString();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); feedback.rate(post.getId(), reader, 2); return true; });
            var second = executor.submit(() -> { start.await(); feedback.rate(post.getId(), reader, 5); return true; });
            start.countDown();
            assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var state = feedback.feedback(post.getId(), reader, 0);
            assertThat(state.votes()).isEqualTo(1);
            assertThat(state.ownStars()).isIn(2, 5);
        } finally { service.delete(post.getId(), posts.findById(post.getId()).orElseThrow().getVersion()); }
    }

    @Test
    void failureAfterArticleFlushRollsBackContentAndRevisionTogether() {
        var post = service.create("原文", "摘要", "内容", PostStatus.DRAFT, null);
        try {
            assertThatThrownBy(() -> service.update(post.getId(), post.getVersion(), "不应保存", "摘要", "修改", PostStatus.PUBLISHED, "x".repeat(301)))
                .isInstanceOf(IllegalArgumentException.class);
            var stored = posts.findById(post.getId()).orElseThrow();
            assertThat(stored.getTitle()).isEqualTo("原文");
            assertThat(stored.getVersion()).isEqualTo(0);
            assertThat(revisions.findByPostIdOrderByNumberDesc(post.getId(), PageRequest.of(0, 10))).hasSize(1);
        } finally { service.delete(post.getId(), posts.findById(post.getId()).orElseThrow().getVersion()); }
    }

    @Test
    void concurrentFirstEditsKeepOneBaselineAndOneWinningRevision() throws Exception {
        var post = posts.saveAndFlush(new Post("旧文", "摘要", "原文", PostStatus.DRAFT));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> edit = () -> {
                start.await();
                try {
                    service.update(post.getId(), 0, "修改", "摘要", "新版", PostStatus.DRAFT, "并发编辑");
                    return true;
                } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
                    return false;
                } catch (org.springframework.web.server.ResponseStatusException conflict) {
                    if (conflict.getStatusCode().value() != 409) throw conflict;
                    return false;
                }
            };
            var first = executor.submit(edit);
            var second = executor.submit(edit);
            start.countDown();
            assertThat(java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                second.get(20, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            var history = revisions.findByPostIdOrderByNumberDesc(post.getId(), PageRequest.of(0, 10));
            assertThat(history).hasSize(2);
            assertThat(history.getContent().getLast().getContent()).isEqualTo("原文");
        } finally {
            service.delete(post.getId(), posts.findById(post.getId()).orElseThrow().getVersion());
        }
    }

    @Test
    void databaseRejectsTwoWritersWhoReadTheSameVersion() {
        var post = service.create("原文", "摘要", "内容", PostStatus.DRAFT, null);
        var first = factory.createEntityManager();
        var second = factory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var a = first.find(Post.class, post.getId());
            var b = second.find(Post.class, post.getId());
            a.update("先保存", "摘要", "内容", PostStatus.DRAFT);
            first.getTransaction().commit();
            b.update("覆盖", "摘要", "内容", PostStatus.DRAFT);
            assertThatThrownBy(second::flush).isInstanceOf(OptimisticLockException.class);
            second.getTransaction().rollback();
            assertThat(posts.findById(post.getId()).orElseThrow().getTitle()).isEqualTo("先保存");
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close(); second.close();
            service.delete(post.getId(), posts.findById(post.getId()).orElseThrow().getVersion());
        }
    }
}
