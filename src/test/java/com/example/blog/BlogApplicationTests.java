package com.example.blog;

import com.example.blog.domain.PostStatus;
import com.example.blog.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class BlogApplicationTests {

	@Autowired
	private PostRepository postRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void devSamplePostsArePublished() {
		assertThat(postRepository.findAll())
			.hasSize(3)
			.allMatch(post -> post.getStatus() == PostStatus.PUBLISHED);
	}

}
