package com.example.blog.web;

import com.example.blog.service.*;
import com.example.blog.domain.PostStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"blog.admin.enabled=true","blog.admin.username=test-author","blog.admin.password=Test-only-long-password-2026"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CommunityWebTest {
    @Autowired MockMvc mvc;
    @Autowired CommunityService community;
    @Autowired PostService posts;
    @Test void readersSeeContentFirstAndNeverSeeManagementOrEditLinks() throws Exception {
        var post=posts.create("最新的生活记录","摘要","正文预览应该显示在首页",PostStatus.PUBLISHED,null);
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("正文预览应该显示在首页")))
            .andExpect(content().string(containsString("身份切换"))).andExpect(content().string(not(containsString("href=\"/admin/"))));
        mvc.perform(get("/posts/{id}",post.getId())).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("href=\"/admin/"))));
        mvc.perform(get("/identity")).andExpect(status().isOk()).andExpect(content().string(containsString("我是作者")));
        for(String path:new String[]{"/admin/overview","/admin/messages"}) mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
    }
    @Test void moodRequiresCsrfAndCookieAndMessageApprovalRemainsPrivate() throws Exception {
        var cookie=mvc.perform(get("/")).andReturn().getResponse().getCookie("reader_id");
        mvc.perform(post("/community/mood").cookie(cookie).param("mood","CALM")).andExpect(status().isForbidden());
        mvc.perform(post("/community/mood").cookie(cookie).with(csrf()).param("mood","CALM"))
            .andExpect(redirectedUrl("/#mood"));
        mvc.perform(post("/community/mood").with(csrf()).param("mood","CALM")).andExpect(status().isBadRequest());
        community.leaveMessage("访客","<script>privateWall=1</script>");
        mvc.perform(get("/")).andExpect(content().string(not(containsString("privateWall"))));
        var message=community.messages("PENDING",0).getFirst();
        mvc.perform(post("/admin/messages/{id}/approve",message.id()).with(csrf()).param("version","0"))
            .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/admin/messages/{id}/approve",message.id()).with(user("test-author")).with(csrf()).param("version","0"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/")).andExpect(content().string(containsString("&lt;script&gt;privateWall=1&lt;/script&gt;")));
    }
    @Test void pollResultsAreOnlyRenderedAfterVotingAndDashboardIsSeparate() throws Exception {
        community.createPoll("选择话题","Java\n生活"); var poll=community.home(null,0).poll();
        var cookie=mvc.perform(get("/")).andExpect(content().string(not(containsString("class=\"poll-count\"")))).andReturn().getResponse().getCookie("reader_id");
        mvc.perform(post("/community/polls/{id}/vote",poll.id()).with(csrf()).cookie(cookie).param("option","1"))
            .andExpect(redirectedUrl("/#community"));
        mvc.perform(get("/").cookie(cookie)).andExpect(content().string(containsString("class=\"poll-count\"")));
        mvc.perform(get("/admin/overview").with(user("test-author"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("作者工作台"))).andExpect(content().string(containsString("首页欢迎语")));
        mvc.perform(get("/admin/messages").with(user("test-author"))).andExpect(status().isOk());
    }
}
