package com.example.blog.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable content snapshots, never directly exposed by public controllers. */
@Entity
@Table(name = "post_revisions", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "revision_number"}))
public class PostRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;
    @Column(name = "revision_number", nullable = false)
    private long number;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 500)
    private String summary;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PostStatus status;
    @Column(nullable = false, length = 300)
    private String changeNote;
    @Column(nullable = false)
    private Instant savedAt;

    protected PostRevision() { }

    public PostRevision(Post post, String note) {
        this.post = post;
        this.number = post.getVersion() + 1;
        this.title = post.getTitle();
        this.summary = post.getSummary();
        this.content = post.getContent();
        this.status = post.getStatus();
        this.changeNote = note;
        this.savedAt = Instant.now();
    }

    public Long getId() { return id; }
    public long getNumber() { return number; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }
    public PostStatus getStatus() { return status; }
    public String getChangeNote() { return changeNote; }
    public Instant getSavedAt() { return savedAt; }
}
