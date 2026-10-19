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
