# Spring Boot 个人博客

这是一个基于 Spring Boot、Thymeleaf 和 Spring Data JPA 的个人博客项目，使用服务端模板渲染页面。

当前支持公开文章列表和详情，以及后台文章发布、编辑、删除和表单校验。本地开发默认使用 H2；项目还包含 MySQL `prod` profile 配置骨架，尚未完成生产数据库验证。

## 项目功能

- 查看文章列表和文章详情
- 在后台发布、编辑和删除文章
- 校验标题、摘要和正文，并显示中文错误提示
- 在 `dev` profile 下使用 H2 和示例文章
- 使用 Thymeleaf 服务端渲染页面

后台目前没有登录或权限管理。请仅在可信的本地开发环境中使用管理页面；公开部署前需要增加访问控制。

## 技术栈

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Thymeleaf
- Spring Data JPA / Hibernate
- Jakarta Bean Validation
- H2（本地开发）
- MySQL Connector/J（生产配置骨架）
- Maven Wrapper
- JUnit Jupiter、MockMvc、Mockito（测试）
- Git / GitHub（版本管理）

依赖版本由 Spring Boot Maven Parent 管理；项目没有为 Hibernate、H2、MySQL Driver 或测试库单独指定版本。

## 项目结构

```text
src/main/java/com/example/blog
├── config       # 开发环境示例文章初始化
├── domain       # Post 文章实体
├── repository   # Spring Data JPA 数据访问接口
└── web          # 页面 Controller 和 PostForm 表单对象

src/main/resources
├── templates    # Thymeleaf 页面
└── application*.yml

src/test/java/com/example/blog
├── BlogApplicationTests.java
└── web          # 公开页面和后台 Controller 测试
```

项目当前没有 Service 层。

## 本地运行

前置条件：安装 Java 21。项目提供 Maven Wrapper，因此不需要单独安装 Maven。

在 Windows PowerShell 中，从项目根目录运行：

```powershell
.\mvnw.cmd spring-boot:run
```

默认配置使用 `dev` profile、H2 内存数据库，并监听端口 `8080`。启动后访问：

- 博客首页：<http://localhost:8080/>
- 后台文章列表：<http://localhost:8080/admin/posts>
- H2 Console：<http://localhost:8080/h2-console>

本地 H2 Console 连接参数（来源于 `application-dev.yml`）：

| 参数 | 值 |
| --- | --- |
| JDBC URL | `jdbc:h2:mem:blog;DB_CLOSE_DELAY=-1;MODE=MySQL` |
| Username | `sa` |
| Password | 留空 |

`dev` 使用 H2 内存数据库和 `create-drop`。应用停止后，数据库内容不会持久保存。开发环境数据库为空时，`DataInitializer` 会加入 3 篇示例文章。

## 主要页面与路由

| 方法 | 路由 | 用途 |
| --- | --- | --- |
| GET | `/` | 显示文章列表 |
| GET | `/posts/{id}` | 显示文章详情 |
| GET | `/admin/posts` | 显示后台文章列表 |
| GET | `/admin/posts/new` | 显示新建文章表单 |
| GET | `/admin/posts/{id}/edit` | 显示文章编辑表单 |
| POST | `/admin/posts` | 校验并创建文章 |
| POST | `/admin/posts/{id}` | 校验并更新文章 |
| POST | `/admin/posts/{id}/delete` | 删除文章 |

## 运行测试

在 Windows PowerShell 的项目根目录运行：

```powershell
.\mvnw.cmd test
```

README 创建前最近一次已知测试记录：

```text
Tests run: 22
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

以上是项目已有的最近验证结果；创建 README 后没有重新运行测试。

## 开发过程

项目通过多个独立 Git commit 分阶段开发，最近的功能提交依次包括：

1. `56b92f8`：建立可运行首页和文章详情
2. `5465f79`：增加文章创建
3. `60eafef`：分离公开页面和后台管理流程
4. `1f41f02`：增加文章编辑和删除
5. `60f4cfb`：增加 `PostForm` 和 Bean Validation
6. `e89268a`：修复编辑表单模式显示

这些提交保留了项目从基础页面到后台功能的开发过程。

## 遇到的问题与解决方案

### 1. Spring Boot 4 测试 API 变化

旧版教程中的 Web MVC 测试注解包路径不一定适用于 Spring Boot 4。项目根据当前使用的测试依赖调整了测试 API；例如 `WebMvcTest` 使用 `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`。

### 2. Thymeleaf 编辑表单 ID

自动测试发现编辑表单 action 没有带上文章 ID：测试使用的文章对象尚未持久化，因此 `post.id` 为空。后来由 Controller 显式向 Model 提供 `postId`，模板据此生成 `/admin/posts/{id}`，不再依赖实体 ID 或内部请求上下文。

### 3. PostForm 重构后的模式判断

表单从直接绑定 `Post` 改为绑定 `PostForm` 后，按钮仍使用旧的 `post` 变量判断新增或编辑模式。检查发现后改为使用 `postId`，并在新增和编辑页面测试中增加按钮文字断言。

## AI 使用与验证

开发过程中使用 AI 辅助环境排查、代码建议、测试设计和 Bug 定位。代码变更经过人工审查，并使用 Maven 自动测试验证；此前也通过浏览器手动完成了发布、查看详情、编辑和删除流程。各阶段还通过 Git diff 和 status 检查变更范围。

AI 生成的建议不一定符合项目使用的 Spring Boot 版本，也可能遗漏模板状态等边界情况，因此需要结合项目代码审查并通过测试验证；AI 并非独立完成整个项目。

## 数据库说明

### dev

`dev` profile 使用 H2 内存数据库，默认在本地开发时启用。数据库为空时，`DataInitializer` 提供 3 篇示例文章。应用停止后，数据库数据不会保留。

### prod

项目包含 MySQL `prod` profile 配置骨架，连接参数可通过 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME` 和 `DB_PASSWORD` 环境变量提供。当前没有数据库迁移脚本，且 `ddl-auto` 使用 `validate`，所以启用 `prod` 前需要自行准备数据库和表结构。

MySQL 生产连接和部署尚未验证；默认运行使用 `dev`，不会自动切换到 `prod`。

## 后续计划

以下均为计划，尚未实现：

- 统一页面视觉样式
- 文章分页和搜索
- Markdown 正文
- 标签或分类
- 生产环境数据库迁移
- 公网部署前增加后台认证和权限控制
