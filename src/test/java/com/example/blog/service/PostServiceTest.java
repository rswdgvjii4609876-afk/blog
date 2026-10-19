package com.example.blog.service;

import com.example.blog.domain.*;
import com.example.blog.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostServiceTest {
    @Autowired PostService service;
    @Autowired PostRepository posts;
    @Autowired PostRevisionRepository revisions;

    @Test
    void snapshotsSurviveEditsAndRestoreAsDraftWithoutOverwritingHistory() {
        var post = service.create("初版", "摘要", "## 初版", PostStatus.DRAFT, "第一次尝试");
        var first = revisions.findByPostIdOrderByNumberDesc(post.getId(), PageRequest.of(0, 10)).getContent().getFirst();
        assertThat(first.getNumber()).isEqualTo(1);
        service.update(post.getId(), post.getVersion(), "发布", "新摘要", "## 完善", PostStatus.PUBLISHED, "补充结论");
        assertThat(post.getVersion()).isEqualTo(1);
        service.restore(post.getId(), first.getId(), post.getVersion());
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.getTitle()).isEqualTo("初版");
        var history = revisions.findByPostIdOrderByNumberDesc(post.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(history).extracting(PostRevision::getNumber).containsExactly(3L, 2L, 1L);
        assertThat(history.getFirst().getChangeNote()).contains("恢复自版本 1");
        assertThat(history.get(1).getContent()).isEqualTo("## 完善");
        assertThat(first.getContent()).isEqualTo("## 初版");
    }

    @Test
    void preservesOriginalContentForArticlesCreatedBeforeRevisionTracking() {
        var post = posts.saveAndFlush(new Post("旧文", "摘要", "原始内容", PostStatus.PUBLISHED));
        service.update(post.getId(), post.getVersion(), "改稿", "摘要", "修改后", PostStatus.PUBLISHED, null);
        var history = revisions.findByPostIdOrderByNumberDesc(post.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(history).hasSize(2);
        assertThat(history.getLast().getContent()).isEqualTo("原始内容");
    }

    @Test
    void rejectsStaleEditRestoreAndDeleteWithoutChangingContent() {
        var post = service.create("原文", "摘要", "内容", PostStatus.PUBLISHED, null);
        long oldVersion = post.getVersion();
        var revision = revisions.findByPostIdOrderByNumberDesc(post.getId(), PageRequest.of(0, 10)).getContent().getFirst();
        service.update(post.getId(), oldVersion, "新版", "摘要", "新内容", PostStatus.PUBLISHED, null);
        assertConflict(() -> service.update(post.getId(), oldVersion, "错误覆盖", "摘要", "覆盖", PostStatus.DRAFT, null));
        assertConflict(() -> service.restore(post.getId(), revision.getId(), oldVersion));
        assertConflict(() -> service.delete(post.getId(), oldVersion));
        assertThat(post.getTitle()).isEqualTo("新版");
    }

    @Test
    void refusesOtherArticlesRevisionAndCleansUpHistoryOnDelete() {
        var first = service.create("一", "摘要", "内容", PostStatus.PUBLISHED, null);
        var second = service.create("二", "摘要", "内容", PostStatus.DRAFT, null);
        var revision = revisions.findByPostIdOrderByNumberDesc(second.getId(), PageRequest.of(0, 10)).getContent().getFirst();
        assertThatThrownBy(() -> service.restore(first.getId(), revision.getId(), first.getVersion()))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        service.delete(first.getId(), first.getVersion());
        assertThat(posts.findById(first.getId())).isEmpty();
        assertThat(revisions.existsByPostId(first.getId())).isFalse();
        assertThat(revisions.existsByPostId(second.getId())).isTrue();
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
}
