package com.example.blog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * 博客文章实体（标题、摘要、正文、状态、创建时间）。
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Post() {
        // JPA requires a no-arg constructor
    }

    public Post(String title, String summary, String content, PostStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.title = title;
        this.summary = summary;
        this.content = content;
    }

    public void update(String title, String summary, String content, PostStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.title = title;
        this.summary = summary;
        this.content = content;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
            updatedAt = createdAt;
        }
    }

    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt == null ? createdAt : updatedAt; }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }
    public PostStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
