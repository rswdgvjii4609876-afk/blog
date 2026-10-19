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
