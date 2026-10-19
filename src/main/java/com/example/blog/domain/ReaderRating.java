package com.example.blog.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "reader_ratings", uniqueConstraints = @UniqueConstraint(name = "uq_reader_rating", columnNames = {"post_id", "reader_id"}))
public class ReaderRating {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id") private Post post;
    @Column(name = "reader_id", nullable = false, length = 36) private String readerId;
    @Column(nullable = false) private int stars;
    protected ReaderRating() { }
    public ReaderRating(Post post, String readerId, int stars) { this.post = post; this.readerId = readerId; this.stars = stars; }
    public void setStars(int stars) { this.stars = stars; }
    public int getStars() { return stars; }
}
