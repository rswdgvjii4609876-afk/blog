package com.example.blog.service;

import com.example.blog.domain.*;
import com.example.blog.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PostService {
    private final PostRepository posts;
    private final PostRevisionRepository revisions;

    public PostService(PostRepository posts, PostRevisionRepository revisions) {
        this.posts = posts;
        this.revisions = revisions;
    }

    public Post create(String title, String summary, String content, PostStatus status, String note) {
        Post saved = posts.save(new Post(title, summary, content, status));
        posts.flush();
        revisions.save(new PostRevision(saved, note(note, "创建文章")));
        return saved;
    }

    public Post update(Long id, long expectedVersion, String title, String summary, String content,
                       PostStatus status, String note) {
        Post post = current(id, expectedVersion);
        PostRevision baseline = baseline(post);
        post.update(title, summary, content, status);
        posts.save(post);
        // Flush increments @Version and detects a concurrent writer before recording its snapshot.
        posts.flush();
        if (baseline != null) revisions.save(baseline);
        revisions.save(new PostRevision(post, note(note, "更新文章")));
        return post;
    }

    public void restore(Long id, Long revisionId, long expectedVersion) {
        Post post = current(id, expectedVersion);
        PostRevision revision = revisions.findByIdAndPostId(revisionId, id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PostRevision baseline = baseline(post);
        // Restore the content, never implicitly re-publish an old draft or withdrawn article.
        post.update(revision.getTitle(), revision.getSummary(), revision.getContent(), PostStatus.DRAFT);
        posts.save(post);
        posts.flush();
        if (baseline != null) revisions.save(baseline);
        revisions.save(new PostRevision(post, "恢复自版本 " + revision.getNumber() + "，保存为草稿"));
    }

    public void delete(Long id, long expectedVersion) {
        Post post = current(id, expectedVersion);
        revisions.deleteByPostId(id);
        posts.delete(post);
        posts.flush();
    }

    private Post current(Long id, long expectedVersion) {
        Post post = posts.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (post.getVersion() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "文章已在其他窗口修改，请保留当前输入，刷新后重新编辑。");
        }
        return post;
    }

    private PostRevision baseline(Post post) {
        // Capture before mutation, insert only after the optimistic article write succeeds.
        // Otherwise two first-time editors could race to insert the same baseline number.
        return revisions.existsByPostId(post.getId()) ? null
            : new PostRevision(post, "启用修订记录前的内容");
    }

    private String note(String input, String fallback) {
        String value = input == null ? "" : input.strip();
        if (value.length() > 300) throw new IllegalArgumentException("修改说明不能超过300个字符");
        return value.isEmpty() ? fallback : value;
    }
}
