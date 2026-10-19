# 知行 · ZHIXING JOURNAL

**不止于学会，更要有所创造。**

一个基于 Java 21 / Spring Boot 的个人技术博客。用 Markdown 记录实践，用修订时间线与逐行对比展示思考如何完善；支持本机使用，以及带管理员认证的公网部署。

![新版首页实机截图](docs/screenshots/home-desktop.png)

## 导航

- [功能与特色](#功能与特色)
- [技术与结构](#技术与结构)
- [本机启动](#本机启动)
- [让其他人访问](#让其他人访问)
- [数据与升级](#数据与升级)
- [验证与自动检查](#验证与自动检查)
- [常见问题](#常见问题)
- [文档与限制](#文档与限制)

## 功能与特色

| 模块 | 已实现 |
| --- | --- |
| 公开阅读 | 已发布文章列表、详情、搜索、分页、无匹配和错误页面 |
| 写作 | Markdown、安全预览、表单校验、草稿、发布、撤回、确认删除 |
| 成长记录 | 每次保存生成快照、修改说明、历史分页、恢复为草稿、并发编辑保护 |
| **版本逐行对比** | 历史与当前内容的源文差异、增删行高亮、双行号、增删统计、标题/摘要/状态对照 |
| **沉浸阅读** | 深色与浅色切换、主题记忆、阅读进度、当前目录定位、代码高亮和复制 |
| 界面 | 深色编辑风格、暖金强调色、统一卡片与字体层级、手机/平板/桌面适配 |
| 对外访问 | 管理员登录、退出、访客权限隔离、登录限流、CSRF、安全响应头 |
| 部署 | 非 root 容器、只读根文件系统、持久卷、数据库健康检查、Caddy HTTPS 代理 |

普通读者无需注册。作者在共享/公网模式中登录后管理文章。默认本机模式仍可直接操作后台。

### 两个重点设计

**文章成长记录**：修订是一份独立快照。恢复不会删除旧历史，而是创建新记录并转为草稿，防止撤回过的内容意外重新公开。旧窗口不能覆盖新窗口已经保存的文章。

**看得见的版本差异**：通过 LCS 找出 Markdown 源文的增删行，输出始终按文字转义。单侧最多 600 行，超过上限明确提示，避免无界二次复杂度影响服务响应。

[查看版本对比截图](docs/screenshots/diff-desktop.png) · [登录页截图](docs/screenshots/login-desktop.png) · [手机首页截图](docs/screenshots/home-mobile.png)

## 技术与结构

- Java 21 **JDK**、Spring Boot 4.1.1、Maven Wrapper。
- Spring MVC、Thymeleaf、Bean Validation。
- Spring Data JPA / Hibernate、H2 文件库、Flyway。
- Spring Security：表单登录、BCrypt、CSRF、CSP、session 安全配置。
- CommonMark、jsoup；本地提供 highlight.js，不依赖运行时 CDN。
- JUnit、MockMvc、Mockito、Playwright / Chromium、Docker / Caddy 集成验证。

```text
src/main/java/com/example/blog/
  config/       安全配置、登录限流、开发示例数据
  domain/       文章、状态、修订快照
  repository/   状态隔离查询、搜索分页、历史存取
  service/      事务写入、安全 Markdown、有限规模版本对比
  web/          页面、登录、预览、健康检查与冲突处理
src/main/resources/
  db/migration/ H2 版本化建表 SQL
  templates/    Thymeleaf 页面
  static/       CSS、主题/阅读交互、本地第三方资源
scripts/        构建后启动脚本
tests/browser/ 本地模式与登录模式浏览器流程
tests/deploy/  Docker、可信 HTTPS、持久卷验收
deploy/         Caddy HTTPS 配置
docs/           设计、部署、验证、演示与截图
```

## 本机启动

### 前提

安装 Java 21 **JDK**，并确认两个命令都能运行：

```text
java -version
javac -version
```

仅有 Java 运行时不够。首次构建需要联网下载 Maven 与依赖。

### Windows

1. 下载代码 ZIP 后**先全部解压**，不要在压缩包中打开脚本。
2. 确认同一文件夹中有 `start.cmd`、`mvnw.cmd` 和 `pom.xml`。
3. 双击 `start.cmd`，等待测试和启动完成；保持命令窗口打开。
4. 浏览器打开 `http://localhost:8080`；后台路径为 `/admin/posts`。

### Linux / macOS

```bash
git clone https://github.com/rswdgvjii4609876-afk/blog.git
cd blog
bash start.sh
```

一键脚本会先执行测试并重新构建，再启动当前代码。也可以分开执行：

```bash
bash mvnw -B -ntp verify
bash scripts/run-dev.sh
```

Windows 的分步命令：

```powershell
.\mvnw.cmd -B -ntp verify
java -Djava.net.preferIPv4Stack=true -jar target/blog-0.0.1-SNAPSHOT.jar
```

默认使用 `dev`，仅监听 `127.0.0.1:8080`，不要求登录。空库初始化 3 篇示例文章。使用 `Ctrl+C` 停止。

## 让其他人访问

**GitHub 保存代码，并不会自动托管 Spring Boot 网站。别人的 `localhost` 指向别人的电脑。**
公网使用需要一台持续运行的服务器/支持 Docker 和持久存储的托管服务，以及 HTTPS 入口。

### 模式选择

| 模式 | 启动参数 | 监听与登录 | 用途 |
| --- | --- | --- | --- |
| 本机 | 默认 `dev` | 本机监听；免登录 | 独立开发与演示 |
| 共享 | `--spring.profiles.active=shared` | 全网卡；必须设置管理员密码 | 可信局域网或部署调试 |
| 公网 | `--spring.profiles.active=public` | 全网卡；认证、Secure session cookie | 必须位于可信 HTTPS 代理之后 |

即使不切换 profile，只要将监听地址改成非本机地址，也会强制启用管理员认证。`public` / `shared` 不能通过关闭一个布尔开关绕过登录。

### 推荐公网部署：Docker Compose + Caddy

需要：Linux 服务器、Docker Engine/Compose、构建用 Java 21 JDK、HTTPS 域名（无域名可用下节公网 IP 方案），以及对外开放的 80/443 端口。

在服务器上：

```bash
git clone https://github.com/rswdgvjii4609876-afk/blog.git
cd blog
bash mvnw -B -ntp verify
cp .env.example .env
chmod 600 .env
```

在服务器本地编辑 `.env`：设置真实 `BLOG_DOMAIN`、管理员账号和自己的随机密码。
密码要求 16–64 个字符且不超过 72 个 UTF-8 字节；不提供默认密码。不要提交 `.env` 或将密码发到聊天中。

然后：

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=80 app
```

域名解析和防火墙正确时，Caddy 自动申请受信任证书并将 HTTP 跳转到 HTTPS。
访客打开你配置的 **HTTPS 域名**，作者通过 `/login` 登录。
应用的 8080 端口只在 Docker 内部网络使用，不应单独对公网开放。

### 没有域名：公网 IP HTTPS

Caddy 2.11.7 与 Let’s Encrypt 的短期 IP 证书可用于公网 IPv4/IPv6，无需先购买域名。
首次部署可以在服务器交互式设置，不在命令行传密码：

```bash
python3 scripts/setup-ip.py 你的公网IPv4
```

按提示输入两次自己的管理员密码（输入时不显示字符）。脚本会创建权限为 600 的私密配置，
启用 IP HTTPS 覆盖文件并启动 Compose；已有配置时拒绝覆盖。实际证书签发仍需检查 Caddy 日志。

也可手动设置 `.env` 中的 `BLOG_DOMAIN` 为自己的公网 IP，然后：

```bash
cp deploy/compose.ip.override.yaml compose.override.yaml
docker compose config --quiet
docker compose up -d --build
```

该本地覆盖文件会自动加载，使用显式 ACME `shortlived` 配置。先开放服务器 TCP 80/443。
证书有效期约六天，保持 Caddy 运行并保留证书卷以自动续期。
普通的默认 IP 配置可能使用本地 CA，不等同于浏览器信任的公网证书。
实际 IP 签发必须在目标服务器上验证；配置解析通过不代表已经签发。

[完整部署、升级、备份与排错说明](docs/DEPLOYMENT.md)

**当前交付状态**：部署镜像、HTTPS 代理和持久化已在隔离环境中实测；仓库不包含替你购买的服务器或域名，也没有预先分配的公开网址。

上线后检查与已知边界见 [检查记录](docs/REVIEW.md)。

## 数据与升级

- 默认本地数据：`data/blog.mv.db`；可用 `BLOG_DATA_DIR` 更改目录。
- Docker 数据：`blog-data` 命名卷；容器重建不会清除文章和历史。
- Flyway 管理建表，Hibernate 只校验结构；H2 Console 默认关闭。
- 测试使用独立内存库或临时文件库，不操作你的开发数据。
- `data/`、`.env`、构建输出和依赖缓存都不会提交到 Git。

**升级前先停止应用并备份数据。**已有 Git 项目使用 `git pull --ff-only`；ZIP 用户将新版解压到新目录，保留备份，再迁移旧 `data/`。不要误删旧目录中的文章。
本次界面、认证与对比升级未修改原有数据库迁移 V1。

公网更新流程与回滚注意事项见 [部署文档](docs/DEPLOYMENT.md)。不要执行 `docker compose down -v`，该命令会删除数据库卷和证书卷。

## 验证与自动检查

### JVM

```bash
bash mvnw -B -ntp verify
```

当前基线：**93 项测试，0 失败、0 错误、0 跳过**。包括原有业务、事务/并发、Markdown 安全、版本对比、认证、限流和数据库迁移。

### 真实浏览器

需要 Node.js 22+：

```bash
cd tests/browser
npm ci
npx playwright install chromium
npm test
```

Linux 缺少浏览器系统库时，按 Playwright 官方方式安装：`npx playwright install --with-deps chromium`。
已有系统 Chromium 可设置 `CHROMIUM_PATH`；测试端口默认为 18080，可通过 `BLOG_TEST_PORT` 修改。

登录模式也有独立验收。Linux / macOS 可运行：

```bash
BLOG_BROWSER_AUTH=1 npm test
```

PowerShell：`$env:BLOG_BROWSER_AUTH="1"; npm test`。
测试密码自动随机生成，不使用真实管理员账号。验收包含深浅主题、目录与进度、差异页面、320/390/768/1440px 布局、两窗口冲突和真实 JVM 重启。

### 容器与 HTTPS

先完成 JAR 构建，再从仓库根目录运行：

```bash
docker build -t zhixing-blog:local .
python3 tests/deploy/verify.py
```

测试创建隔离网络、数据卷和 Caddy 测试证书，客户端显式信任生成的测试 CA，**不关闭证书或域名校验**。
验证缺少密码拒绝启动、HTTPS、Secure cookie、认证、非 root/只读容器、健康检查和数据持久化；完成后清理自己的测试资源。
这不等于真实域名的公网证书已申请成功。

[GitHub Actions 工作流](.github/workflows/verify.yml) 执行构建、两种浏览器模式和容器 HTTPS 验收。远程运行结果以仓库 Actions 页面实际记录为准。

## 常见问题

| 现象 | 处理 |
| --- | --- |
| `mvnw.cmd` 不是内部或外部命令 | 先全部解压 ZIP，在包含 `pom.xml` 的完整项目目录启动 |
| `java` / `javac` 找不到或 release 21 不支持 | 安装 Java 21 JDK，检查 PATH 与 JAVA_HOME 指向同一个 JDK |
| 浏览器拒绝连接 | 等到 `Started BlogApplication`，不要关闭启动窗口；确认端口为 8080 |
| 8080 被占用 | `bash scripts/run-dev.sh --server.port=8081`；Windows 给 JAR 命令追加同一参数 |
| 更新后仍是旧界面 | 确认运行的是新版目录，重新构建并启动，再强制刷新浏览器 |
| 公网模式启动失败提示密码 | 在服务器环境或 `.env` 配置自己的有效管理员密码 |
| 公网页面可读但登录反复跳转 | 使用 HTTPS；检查可信代理的转发头，不能直接用 HTTP 访问 public 模式 |
| 登录返回 429 | 同一 IP 一分钟最多 10 次登录请求，等待一分钟后再试 |
| 忘记管理员密码 | 在服务器仓库目录运行 `python3 scripts/reset-admin-password.py`，隐藏输入两次新密码；保留数据与证书 |
| 容器不健康 | 查看 app 日志及 `/healthz`，检查数据卷权限、内存和迁移错误 |
| 新环境没有示例文章 | 只有 dev 会初始化示例；公网空库从作者后台开始创建 |

## 文档与限制

- [设计与技术取舍](docs/ARCHITECTURE.md)
- [完整部署与运维](docs/DEPLOYMENT.md)
- [验证记录与 AI 协作](docs/VALIDATION.md)
- [面试演示路线](docs/DEMO.md)
- [升级过程](docs/UPGRADE.md)
- [第三方资源与许可](docs/THIRD_PARTY.md)

当前面向**单作者、单实例、小型个人博客**。H2 文件卷不能由多个应用副本同时写入；扩容前需要外部数据库与共享会话/限流方案。没有访客注册、评论、图片上传、历史压缩或自动备份。
MySQL `prod` 仍是单独的未验收骨架，不要将 H2 的 Flyway SQL 直接用于 MySQL。

Git 保留原有开发历史，升级按真实阶段提交。AI 协助实现与排查，验证证据、取舍和未覆盖范围均在文档中记录，方便作者理解和现场讲解。
