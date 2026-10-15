package com.example.blog.repository;

import com.example.blog.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 博客文章的数据访问层。
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
}
