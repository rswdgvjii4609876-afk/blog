# 第三方静态资源

`src/main/resources/static/vendor/highlight/` 来自 npm 包
`@highlightjs/cdn-assets@11.11.1`，通过 npm 的完整性校验获取。
保留上游 BSD-3-Clause LICENSE。使用预构建 common languages bundle 和 github-dark 样式。

更新时使用 `npm pack @highlightjs/cdn-assets@<version>` 获取官方包并检查许可，
复制 `highlight.min.js`、`styles/github-dark.min.css`、`LICENSE`；不引用运行时 CDN。
Markdown 解析（CommonMark）和清洗（jsoup）版本固定于 pom.xml。
