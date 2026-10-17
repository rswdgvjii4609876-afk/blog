package com.example.blog.repository;

import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 博客文章的数据访问层。
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    @Query("""
        select p from Post p
        where p.status = :status
          and (lower(p.title) like lower(concat('%', concat(:#{escape(#keyword)}, '%'))) escape :#{escapeCharacter()}
            or lower(p.summary) like lower(concat('%', concat(:#{escape(#keyword)}, '%'))) escape :#{escapeCharacter()})
        """)
    Page<Post> searchByStatus(@Param("status") PostStatus status, @Param("keyword") String keyword, Pageable pageable);

    Page<Post> findByTitleContainingIgnoreCaseOrSummaryContainingIgnoreCase(
        String titleKeyword, String summaryKeyword, Pageable pageable);

    Optional<Post> findByIdAndStatus(Long id, PostStatus status);
}
