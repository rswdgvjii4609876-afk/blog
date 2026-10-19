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
                    """
                    欢迎来到我的个人博客。这里我会记录学习笔记、技术心得和生活随想。

                    ## 为什么要写下来

                    学过不等于掌握。把一个概念解释清楚，把一个例子真正跑通，才算向前走了一步。

                    > 好的笔记不仅记录答案，也记录当时为什么这样选择。

                    ## 我的记录方式

                    1. **问题**：我遇到了什么？
                    2. **尝试**：我试过哪些办法，为什么有的没用？
                    3. **验证**：用什么证据判断问题已经解决？
                    4. **复盘**：下次遇到类似情况，可以怎样更快定位？

                    ## 从一篇草稿开始

                    不必等到理解一切才开始写。先保存草稿，补充代码和验证结果，再发布。
                    内容有了新进展，就留下一条修改说明，让每次完善都有迹可循。
                    """,
                    PostStatus.PUBLISHED
                ),
                new Post(
                    "Spring Boot 起步笔记",
                    "从 Spring Initializr 到第一个可运行的 Web 应用。",
                    """
                    本文记录了如何用 Spring Initializr 生成项目、用 Maven Wrapper 构建、以及常见的启动期问题排查。

                    ## 先确认工具链

                    本项目需要 Java 21 **JDK**。`java -version` 只能说明运行时可用，
                    还需要用 `javac -version` 确认编译器已安装。

                    ```bash
                    java -version
                    javac -version
                    bash mvnw -B -ntp verify
                    bash scripts/run-dev.sh
                    ```

                    Windows 使用 `mvnw.cmd verify` 构建，再通过 `java -jar` 启动生成的 JAR。

                    ## 把职责放在合适的位置

                    - Controller 接收请求、校验表单、组织页面。
                    - Service 决定文章与修订记录如何一起保存。
                    - Repository 负责查询，公开查询必须带发布状态条件。

                    ```java
                    public enum PostStatus {
                        DRAFT,
                        PUBLISHED
                    }
                    ```

                    ## 如何判断真的成功

                    看到启动日志只是开始。还需要实际打开首页、查看文章、保存草稿，
                    并确认草稿的公开地址返回 404。自动测试与真实浏览器检查互相补充。
                    """,
                    PostStatus.PUBLISHED
                ),
                new Post(
                    "为什么用 Thymeleaf",
                    "服务端渲染对个人博客来说简单可靠。",
                    """
                    Thymeleaf 是 Spring 生态里最常用的模板引擎之一，语法接近 HTML，对初学者友好。

                    ## 先从需求出发

                    个人博客以内容展示、表单提交为主。服务端渲染让我能专注于数据与页面之间的关系，
                    同时减少前后端独立部署、接口认证与状态同步的成本。

                    ## 普通文字与富文本的区别

                    标题和摘要是普通文字，模板必须自动转义；文章正文是 Markdown，
                    只有经过统一的解析与清洗后，才可以作为 HTML 输出。

                    ```html
                    <h1 th:text="${post.title}">文章标题</h1>
                    ```

                    > 便捷不能替代边界：预览与正式页面必须使用同一套安全规则。

                    ## 这次选择的取舍

                    - 保留普通 HTML 表单，即使没有 JavaScript 也能保存文章。
                    - JavaScript 只增强预览、代码复制和离页提醒。
                    - 暂不支持正文图片，避免引入远程跟踪与上传管理问题。
                    - 没有登录的后台只用于本机开发，不能直接当作公网产品部署。
                    """,
                    PostStatus.PUBLISHED
                )
            );
            postRepository.saveAll(samples);
            log.info("Seeded {} sample posts for dev profile", samples.size());
        };
    }
}
