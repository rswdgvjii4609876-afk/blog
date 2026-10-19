package com.example.blog.repository;

import com.example.blog.domain.ReaderRating;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface ReaderRatingRepository extends JpaRepository<ReaderRating, Long> {
    interface Summary { long getVotes(); Double getAverage(); }
    @Query("select count(r) as votes, avg(r.stars) as average from ReaderRating r where r.post.id = :postId")
    Summary summarize(Long postId);
    Optional<ReaderRating> findByPostIdAndReaderId(Long postId, String readerId);
    @Modifying @Query("delete from ReaderRating r where r.post.id = :postId")
    void deleteForPost(Long postId);
}
