package com.example.blog.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reader_comments")
public class ReaderComment {
    public enum Status { PENDING, APPROVED, HIDDEN }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id") private Post post;
    @Column(nullable = false, length = 40) private String nickname;
    @Column(nullable = false, length = 2000) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version private long version;
    protected ReaderComment() { }
    public ReaderComment(Post post, String nickname, String body) {
        this.post = post; this.nickname = nickname; this.body = body;
        this.status = Status.PENDING; this.createdAt = Instant.now();
    }
    public void moderate(Status status) { this.status = status; }
    public Long getId() { return id; }
    public Post getPost() { return post; }
    public String getNickname() { return nickname; }
    public String getBody() { return body; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
