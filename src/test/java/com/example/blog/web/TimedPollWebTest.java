package com.example.blog.web;

import com.example.blog.service.CommunityService;
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
class TimedPollWebTest {
    @Autowired MockMvc mvc;
    @Autowired CommunityService service;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void privateResponsesAreNeverRenderedPubliclyOrInStatusJsonEvenForTheirOwnBrowser() throws Exception {
        service.createPoll("问题",java.util.List.of("甲","乙"),1,true); long id=service.home(null,0).poll().id();
        var cookie=mvc.perform(get("/")).andReturn().getResponse().getCookie("reader_id");
        mvc.perform(post("/community/polls/{id}/vote",id).with(csrf()).cookie(cookie).param("option","6").param("otherText","<script>privateOther=1</script>"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/").cookie(cookie)).andExpect(content().string(not(containsString("privateOther"))));
        mvc.perform(get("/community/polls/{id}/status",id).cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.votes").value(1)).andExpect(content().string(not(containsString("privateOther"))));
        mvc.perform(get("/community/polls/{id}/status",id)).andExpect(jsonPath("$.results").isEmpty());
        mvc.perform(get("/admin/community/polls/{id}/other",id)).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/community/polls/{id}/other",id).with(user("test-author"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;script&gt;privateOther=1&lt;/script&gt;")));
    }
    @Test void onlyAuthorWithCsrfCanCreateOrExtendAndExpiredStatusShowsPublicResults() throws Exception {
        mvc.perform(post("/admin/community/polls").with(csrf()).param("question","问题").param("options","甲","乙"))
            .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/admin/community/polls").with(user("test-author")).param("question","问题").param("options","甲","乙"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/admin/community/polls").with(user("test-author")).with(csrf())
            .param("question","问题").param("options","甲","乙").param("duration","custom").param("customHours","2").param("otherEnabled","true"))
            .andExpect(redirectedUrl("/admin/overview"));
        var poll=service.home(null,0).poll();
        mvc.perform(post("/admin/community/polls/{id}/extend",poll.id()).with(csrf()).param("version","0").param("hours","1"))
            .andExpect(redirectedUrl("/login"));
        var cookie=mvc.perform(get("/")).andReturn().getResponse().getCookie("reader_id");
        jdbc.update("update reader_polls set ends_at=? where id=?",java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusHours(1),poll.id());
        mvc.perform(post("/community/polls/{id}/vote",poll.id()).with(csrf()).cookie(cookie).param("option","0"))
            .andExpect(status().isConflict());
        mvc.perform(get("/community/polls/{id}/status",poll.id())).andExpect(jsonPath("$.open").value(false))
            .andExpect(jsonPath("$.results.length()").value(3));
        mvc.perform(get("/")).andExpect(content().string(containsString("投票已结束")));
        mvc.perform(post("/admin/community/polls/{id}/extend",poll.id()).with(user("test-author")).with(csrf()).param("version","0").param("hours","1"))
            .andExpect(redirectedUrl("/admin/overview"));
        mvc.perform(get("/community/polls/{id}/status",poll.id())).andExpect(jsonPath("$.open").value(true));
    }
}
