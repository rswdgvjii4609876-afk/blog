package com.example.blog.repository;

import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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
    void publishedQueryExcludesDraftsAndKeepsIdAscendingOrder() {
        Post first = postRepository.save(new Post("第一篇", "摘要", "正文", PostStatus.PUBLISHED));
        postRepository.save(new Post("草稿", "摘要", "正文", PostStatus.DRAFT));
        Post second = postRepository.save(new Post("第二篇", "摘要", "正文", PostStatus.PUBLISHED));
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findByStatus(PostStatus.PUBLISHED, Sort.by(Sort.Direction.ASC, "id")))
            .extracting(Post::getId)
            .containsExactly(first.getId(), second.getId());
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
}
