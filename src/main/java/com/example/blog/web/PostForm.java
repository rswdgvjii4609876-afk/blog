package com.example.blog.web;

import com.example.blog.domain.PostStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PostForm {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过100个字符")
    private String title;

    @NotBlank(message = "摘要不能为空")
    @Size(max = 300, message = "摘要不能超过300个字符")
    private String summary;

    @NotBlank(message = "正文不能为空")
    @Size(max = 100000, message = "正文不能超过100000个字符")
    private String content;

    @NotNull(message = "请选择文章状态")
    private PostStatus status;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public PostStatus getStatus() {
        return status;
    }

    public void setStatus(PostStatus status) {
        this.status = status;
    }
}
