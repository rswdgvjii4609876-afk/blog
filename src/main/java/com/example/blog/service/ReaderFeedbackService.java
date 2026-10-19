package com.example.blog.service;

import com.example.blog.domain.*;
import com.example.blog.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ReaderFeedbackService {
    private final PostRepository posts;
    private final ReaderCommentRepository comments;
    private final ReaderRatingRepository ratings;
    public ReaderFeedbackService(PostRepository posts, ReaderCommentRepository comments, ReaderRatingRepository ratings) {
        this.posts = posts; this.comments = comments; this.ratings = ratings;
    }
    public record Feedback(Page<ReaderComment> comments, long votes, double average, Integer ownStars) { }
    public Feedback feedback(Long postId, String readerId, int page) {
        var summary = ratings.summarize(postId);
        return new Feedback(comments.findByPostIdAndStatus(postId, ReaderComment.Status.APPROVED, pageable(page)),
            summary.getVotes(), summary.getAverage() == null ? 0 : summary.getAverage(),
            readerId == null ? null : ratings.findByPostIdAndReaderId(postId, readerId).map(ReaderRating::getStars).orElse(null));
    }
    @Transactional
    public void comment(Long postId, String nickname, String body) {
        if (nickname == null || nickname.isBlank() || nickname.strip().length() > 40
            || body == null || body.isBlank() || body.strip().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        comments.save(new ReaderComment(published(postId), nickname.strip(), body.strip()));
    }
    @Transactional
    public void rate(Long postId, String readerId, int stars) {
        if (stars < 1 || stars > 5 || readerId == null || !readerId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        // Serialize votes for one article, including first votes from the same browser.
        // A DB unique constraint remains the final protection against duplicate rows.
        var post = published(postId);
        var rating = ratings.findByPostIdAndReaderId(postId, readerId)
            .orElseGet(() -> new ReaderRating(post, readerId, stars));
        rating.setStars(stars);
        ratings.save(rating);
    }
    public Page<ReaderComment> moderation(ReaderComment.Status status, int page) {
        return comments.findByStatus(status, pageable(page));
    }
    @Transactional
    public void moderate(Long id, long version, String action) {
        var comment = comments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (comment.getVersion() != version) throw new ResponseStatusException(HttpStatus.CONFLICT);
        switch (action) {
            case "approve" -> comment.moderate(ReaderComment.Status.APPROVED);
            case "hide" -> comment.moderate(ReaderComment.Status.HIDDEN);
            case "delete" -> comments.delete(comment);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
    private Post published(Long id) {
        return posts.lockPublished(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private Pageable pageable(int page) {
        if (page < 0 || (long) page * 10 > Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return PageRequest.of(page, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
