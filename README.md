# Spring Boot 个人博客

这是一个基于 Spring Boot、Thymeleaf 和 Spring Data JPA 的个人博客项目，使用服务端模板渲染页面。

当前支持公开文章列表和详情、草稿与已发布状态切换、首页与后台分页，以及后台文章创建、编辑、删除和表单校验。本地开发默认使用 H2；项目还包含 MySQL `prod` profile 配置骨架，尚未完成生产数据库验证。

## 项目功能

- 查看已发布文章列表和文章详情
- 新建文章时可选择“保存草稿”（`DRAFT`）或“发布文章”（`PUBLISHED`）
- 编辑时可在草稿与已发布之间切换，并同时保存内容和状态
- 后台显示全部文章及“草稿 / 已发布”状态；草稿仍可编辑和删除，但没有公开“查看”入口
- 首页按 5 篇/页展示已发布文章，后台按 10 篇/页展示全部文章
- 校验标题、摘要和正文，并显示中文错误提示
- 在 `dev` profile 下使用 H2，并初始化 3 篇 `PUBLISHED` 示例文章
- 使用 Thymeleaf 服务端渲染页面

公开列表与详情的 Repository 查询条件都限制为 `PUBLISHED`。草稿不会出现在首页，直接访问草稿的 `/posts/{id}` 与访问不存在的文章一样返回 `404`。

`createdAt` 表示文章创建时间，发布或撤回不会改变该字段；当前没有独立的发布时间字段。

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
├── domain       # Post 文章实体和 PostStatus 状态枚举
├── repository   # Spring Data JPA 数据访问接口
└── web          # 页面 Controller 和 PostForm 表单对象

src/main/resources
├── templates    # Thymeleaf 页面
└── application*.yml

src/test/java/com/example/blog
├── BlogApplicationTests.java
├── repository   # Repository 状态过滤和分页测试
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
| 数据库用户 | `sa` |

`dev` 使用 H2 内存数据库和 `create-drop`。应用停止后，数据库内容不会持久保存。开发环境数据库为空时，`DataInitializer` 会加入 3 篇状态均为 `PUBLISHED` 的示例文章。

## 主要页面与路由

| 方法 | 路由 | 用途 |
| --- | --- | --- |
| GET | `/?page=0` | 显示已发布文章列表，每页 5 篇；`page` 可省略 |
| GET | `/posts/{id}` | 显示已发布文章详情；草稿或不存在的文章返回 `404` |
| GET | `/admin/posts?page=0` | 显示全部文章及状态，每页 10 篇；`page` 可省略 |
| GET | `/admin/posts/new` | 显示新建文章表单 |
| GET | `/admin/posts/{id}/edit` | 显示文章编辑表单 |
| POST | `/admin/posts` | 校验并创建草稿或已发布文章 |
| POST | `/admin/posts/{id}` | 校验并更新文章内容及状态 |
| POST | `/admin/posts/{id}/delete` | 删除文章 |

两个列表均按 `createdAt DESC, id DESC` 排序。URL 的 `page` 使用 0-based 页码，默认 `0`；界面使用 1-based 页码，例如 `page=0` 显示“第 1 / 2 页”。页大小与排序固定，不提供自定义参数。

负数、非整数或超出分页偏移范围的 `page` 返回 `400`；合法但超出实际总页数的页码返回 `404`。没有文章时，第 0 页仍正常显示空列表。总文章数统计符合查询条件的全部文章，翻页不会改变总数；公开列表不计入草稿。

## 运行测试

在 Windows PowerShell 的项目根目录运行：

```powershell
.\mvnw.cmd test
```

当前功能阶段最终测试基线：

```text
Tests run: 45
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

该基线来自草稿发布工作流与分页功能提交前的最终测试，覆盖应用初始化、Repository 查询和公开 / 后台 Controller 行为。本次仅更新文档，没有重新运行 Maven 测试。

## 开发过程

项目通过多个独立 Git commit 分阶段开发，最近的功能提交依次包括：

1. `56b92f8`：建立可运行首页和文章详情
2. `5465f79`：增加文章创建
3. `60eafef`：分离公开页面和后台管理流程
4. `1f41f02`：增加文章编辑和删除
5. `60f4cfb`：增加 `PostForm` 和 Bean Validation
6. `e89268a`：修复编辑表单模式显示
7. `da3d71f feat: add draft and publish workflow`：增加草稿与已发布状态及公开查询隔离
8. `c9b4e36 feat: add post pagination`：增加首页与后台文章分页

这些提交保留了项目从基础页面到后台管理、草稿发布工作流和分页功能的开发过程。

## 遇到的问题与解决方案

### 1. Spring Boot 4 测试 API 变化

旧版教程中的 Web MVC 测试注解包路径不一定适用于 Spring Boot 4。项目根据当前使用的测试依赖调整了测试 API；例如 `WebMvcTest` 使用 `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`。

### 2. Thymeleaf 编辑表单 ID

自动测试发现编辑表单 action 没有带上文章 ID：测试使用的文章对象尚未持久化，因此 `post.id` 为空。后来由 Controller 显式向 Model 提供 `postId`，模板据此生成 `/admin/posts/{id}`，不再依赖实体 ID 或内部请求上下文。

### 3. PostForm 重构后的模式判断

表单从直接绑定 `Post` 改为绑定 `PostForm` 后，按钮仍使用旧的 `post` 变量判断新增或编辑模式。检查发现后改为使用 `postId`，并在新增和编辑页面测试中增加按钮文字断言。

### 4. 草稿的公开访问隔离

仅在模板中隐藏草稿和“查看”入口，仍可能让人通过文章 ID 访问草稿。因此公开列表使用 `findByStatus(PUBLISHED, pageable)`，公开详情使用 `findByIdAndStatus(id, PUBLISHED)`，由 Repository 查询条件限制状态；查不到时统一返回 `404`。

### 5. 分页排序稳定性

多篇文章可能具有相同的创建时间。仅按 `createdAt DESC` 排序时，这些文章之间没有确定的顺序，因此增加 `id DESC` 作为第二排序条件，保证相同创建时间下的分页排序稳定。

## AI 使用与验证

开发过程中使用 AI 辅助环境排查、代码建议、测试设计和 Bug 定位。验证方式包括 Maven tests（自动测试）、git diff（变更审查）、HTTP 检查和浏览器人工验收，并通过 git status 检查变更范围。

草稿发布工作流已通过浏览器人工验收：草稿不出现在首页，公开详情返回 `404`；发布后可访问，撤回后再次返回 `404`。分页也完成了 HTTP 检查和浏览器人工验收，包括首页 6 篇文章按 5 + 1 分页、页码显示、前后页按钮边界、排序及总文章数。

AI 生成的建议不一定符合项目使用的 Spring Boot 版本，也可能遗漏模板状态等边界情况，因此需要结合项目代码审查并通过测试验证；AI 并非独立完成整个项目。

## 数据库说明

### dev

`dev` profile 使用 H2 内存数据库，默认在本地开发时启用。数据库为空时，`DataInitializer` 提供 3 篇状态均为 `PUBLISHED` 的示例文章，因此首页仍可显示初始化内容。应用停止后，数据库数据不会保留。

### prod

项目包含 MySQL `prod` profile 配置骨架，连接参数可通过环境变量提供，具体配置见 `application-prod.yml`。当前没有数据库迁移脚本，且 `ddl-auto` 使用 `validate`，所以启用 `prod` 前需要自行准备与实体一致的数据库和表结构，包括文章状态字段。

MySQL 生产连接和部署尚未验证；默认运行使用 `dev`，不会自动切换到 `prod`。

## 后续计划

以下均为计划，尚未实现：

- 文章搜索
- Markdown 正文
- 标签或分类
- MySQL 实际连接与表结构验证
- 生产环境数据库迁移
- 公网部署前增加后台认证和权限控制
