package com.example.blog.repository;

import com.example.blog.domain.ReaderComment;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ReaderCommentRepository extends JpaRepository<ReaderComment, Long> {
    Page<ReaderComment> findByPostIdAndStatus(Long postId, ReaderComment.Status status, Pageable pageable);
    @EntityGraph(attributePaths = "post")
    Page<ReaderComment> findByStatus(ReaderComment.Status status, Pageable pageable);
    @Modifying @Query("delete from ReaderComment c where c.post.id = :postId")
    void deleteForPost(Long postId);
}
