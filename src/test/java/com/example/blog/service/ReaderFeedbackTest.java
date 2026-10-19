package com.example.blog.service;

import com.example.blog.domain.*;
import com.example.blog.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class ReaderFeedbackTest {
    @Autowired PostService posts;
    @Autowired ReaderFeedbackService feedback;
    @Autowired ReaderCommentRepository comments;
    @Autowired ReaderRatingRepository ratings;
    @Autowired JdbcTemplate jdbc;

    @Test void onlyApprovedCommentsArePublicAndStaleModerationIsRejected() {
        var post = posts.create("讨论", "摘要", "正文", PostStatus.PUBLISHED, null);
        feedback.comment(post.getId(), " 读者 ", " <script>alert(1)</script> ");
        assertThat(feedback.feedback(post.getId(), null, 0).comments()).isEmpty();
        var comment = comments.findByPostIdAndStatus(post.getId(), ReaderComment.Status.PENDING,
            org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst();
        assertThat(comment.getNickname()).isEqualTo("读者");
        feedback.moderate(comment.getId(), comment.getVersion(), "approve");
        comments.flush();
        assertThat(feedback.feedback(post.getId(), null, 0).comments()).hasSize(1);
        assertThatThrownBy(() -> feedback.moderate(comment.getId(), 0, "hide"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        feedback.moderate(comment.getId(), comment.getVersion(), "hide");
        assertThat(feedback.feedback(post.getId(), null, 0).comments()).isEmpty();
    }
    @Test void repeatRatingsUpdateOneVoteAndAverageIsCalculatedFromDistinctRows() {
        var post = posts.create("评分", "摘要", "正文", PostStatus.PUBLISHED, null);
        String first = UUID.randomUUID().toString();
        feedback.rate(post.getId(), first, 2);
        feedback.rate(post.getId(), first, 5);
        feedback.rate(post.getId(), UUID.randomUUID().toString(), 3);
        var state = feedback.feedback(post.getId(), first, 0);
        assertThat(state.votes()).isEqualTo(2);
        assertThat(state.average()).isEqualTo(4.0);
        assertThat(state.ownStars()).isEqualTo(5);
    }
    @Test void draftsAndInvalidFeedbackCannotBeWritten() {
        var draft = posts.create("草稿", "摘要", "正文", PostStatus.DRAFT, null);
        assertThatThrownBy(() -> feedback.comment(draft.getId(), "读者", "内容")).hasMessageContaining("404");
        assertThatThrownBy(() -> feedback.rate(draft.getId(), UUID.randomUUID().toString(), 3)).hasMessageContaining("404");
        for (int stars : new int[]{-1, 0, 6}) {
            assertThatThrownBy(() -> feedback.rate(draft.getId(), UUID.randomUUID().toString(), stars)).hasMessageContaining("400");
        }
        assertThatThrownBy(() -> feedback.comment(draft.getId(), " ", "内容")).hasMessageContaining("400");
        assertThatThrownBy(() -> feedback.comment(draft.getId(), "读者", "x".repeat(2001))).hasMessageContaining("400");
    }
    @Test void deletingArticleRemovesItsFeedbackWithoutAffectingOtherArticles() {
        var first = posts.create("删除", "摘要", "正文", PostStatus.PUBLISHED, null);
        var second = posts.create("保留", "摘要", "正文", PostStatus.PUBLISHED, null);
        for (var post : java.util.List.of(first, second)) {
            feedback.comment(post.getId(), "读者", "评论");
            feedback.rate(post.getId(), UUID.randomUUID().toString(), 5);
        }
        posts.delete(first.getId(), first.getVersion());
        assertThat(jdbc.queryForObject("select count(*) from reader_comments where post_id = ?", Integer.class, first.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reader_ratings where post_id = ?", Integer.class, first.getId())).isZero();
        assertThat(feedback.feedback(second.getId(), null, 0).votes()).isEqualTo(1);
    }
    @Test void commentAndRatingLimitsAreIndependentAndPerAddress() {
        var limiter = new ReaderWriteLimiter();
        for (int i = 0; i < 5; i++) limiter.check("192.0.2.1", true);
        assertThatThrownBy(() -> limiter.check("192.0.2.1", true)).hasMessageContaining("429");
        for (int i = 0; i < 15; i++) limiter.check("192.0.2.1", false);
        assertThatThrownBy(() -> limiter.check("192.0.2.1", false)).hasMessageContaining("429");
        assertThatCode(() -> limiter.check("192.0.2.2", true)).doesNotThrowAnyException();
    }
}
