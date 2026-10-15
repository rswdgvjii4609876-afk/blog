package com.example.blog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 博客文章实体（最小字段：标题、摘要、正文、创建时间）。
 */
@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Post() {
        // JPA requires a no-arg constructor
    }

    public Post(String title, String summary, String content) {
        this.title = title;
        this.summary = summary;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
