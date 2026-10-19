package com.example.blog.repository;

import com.example.blog.domain.PostRevision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PostRevisionRepository extends JpaRepository<PostRevision, Long> {
    Page<PostRevision> findByPostIdOrderByNumberDesc(Long postId, Pageable pageable);
    Optional<PostRevision> findByIdAndPostId(Long id, Long postId);
    boolean existsByPostId(Long postId);
    void deleteByPostId(Long postId);
}
