package com.example.blog.repository;

import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 博客文章的数据访问层。
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByStatus(PostStatus status, Sort sort);

    Optional<Post> findByIdAndStatus(Long id, PostStatus status);
}
