package com.example.blog.config;

import com.example.blog.domain.Post;
import com.example.blog.domain.PostStatus;
import com.example.blog.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * 仅在 dev profile 启动时插入示例文章，方便本地立刻看到非空列表。
 * 生产环境（prod profile）不会执行，不会污染真实数据库。
 */
@Configuration
@Profile("dev")
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public ApplicationRunner seedSamplePosts(PostRepository postRepository) {
        return args -> {
            if (postRepository.count() > 0) {
                log.info("Sample posts already present (count={}), skipping seeding", postRepository.count());
                return;
            }
            List<Post> samples = List.of(
                new Post(
                    "你好，世界",
                    "这是个人博客的第一篇文章。",
                    "欢迎来到我的个人博客。这里我会记录学习笔记、技术心得和生活随想。",
                    PostStatus.PUBLISHED
                ),
                new Post(
                    "Spring Boot 起步笔记",
                    "从 Spring Initializr 到第一个可运行的 Web 应用。",
                    "本文记录了如何用 Spring Initializr 生成项目、用 Maven Wrapper 构建、以及常见的启动期问题排查。",
                    PostStatus.PUBLISHED
                ),
                new Post(
                    "为什么用 Thymeleaf",
                    "服务端渲染对个人博客来说简单可靠。",
                    "Thymeleaf 是 Spring 生态里最常用的模板引擎之一，语法接近 HTML，对初学者友好。",
                    PostStatus.PUBLISHED
                )
            );
            postRepository.saveAll(samples);
            log.info("Seeded {} sample posts for dev profile", samples.size());
        };
    }
}
