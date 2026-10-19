package com.example.blog.web;

import com.example.blog.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"blog.admin.enabled=true", "blog.admin.username=test-author", "blog.admin.password=Test-only-long-password-2026"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicAccessTest {
    @Autowired MockMvc mvc;

    @Test void visitorsReadFreelyButCannotReadOrWriteAnyAdminEndpoint() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        for (String path : new String[]{"/admin/posts", "/admin/posts/new", "/admin/posts/1/history", "/admin/posts/1/history/1/compare"}) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        }
        mvc.perform(post("/admin/markdown/preview").with(csrf()).contentType("text/plain").content("# private"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/admin/posts").with(csrf())).andExpect(status().is3xxRedirection());
    }

    @Test void loginPageAndPasswordAuthenticationWorkWithoutExposingCredentials() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
            .andExpect(content().string(containsString("作者入口")))
            .andExpect(content().string(not(containsString("Test-only-long-password-2026"))));
        mvc.perform(post("/login").with(csrf()).param("username", "test-author").param("password", "wrong"))
            .andExpect(redirectedUrl("/login?error"));
        var loggedIn = mvc.perform(post("/login").with(csrf()).param("username", "test-author")
                .param("password", "Test-only-long-password-2026"))
            .andExpect(redirectedUrl("/admin/posts")).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) loggedIn.getRequest().getSession(false);
        mvc.perform(get("/admin/posts/new").session(session)).andExpect(status().isOk())
            .andExpect(content().string(containsString("退出登录")));
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/?signedout"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void adminStillRequiresCsrfAndUnknownPostsStayHidden() throws Exception {
        mvc.perform(post("/admin/posts").with(user("test-author").roles("ADMIN")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/admin/posts/999999/history").with(user("test-author").roles("ADMIN")))
            .andExpect(status().isNotFound());
    }

    @Test void loginAttemptsAreRateLimitedWithoutDisablingCsrf() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/login").with(csrf()).with(request -> {
                request.setRemoteAddr("192.0.2.19"); request.setServletPath("/login"); return request;
            }).param("username", "test-author").param("password", "incorrect"))
                .andExpect(status().is3xxRedirection());
        }
        mvc.perform(post("/login").with(csrf()).with(request -> {
            request.setRemoteAddr("192.0.2.19"); request.setServletPath("/login"); return request;
        }).param("username", "test-author").param("password", "incorrect"))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
    }

    @Test void sharedProfilesFailClosedAndCannotTurnOffAuthentication() {
        var exposed = new MockEnvironment().withProperty("server.address", "0.0.0.0");
        assertThat(SecurityConfig.loginRequired(exposed)).isTrue();
        assertThatThrownBy(() -> new SecurityConfig().users(exposed)).isInstanceOf(IllegalStateException.class);
        for (String profile : new String[]{"public", "shared"}) {
            var environment = new MockEnvironment().withProperty("blog.admin.enabled", "false");
            environment.setActiveProfiles(profile);
            assertThat(SecurityConfig.loginRequired(environment)).isTrue();
            assertThatThrownBy(() -> new SecurityConfig().users(environment))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BLOG_ADMIN_PASSWORD");
            environment.setProperty("blog.admin.password", "弱".repeat(30));
            assertThatThrownBy(() -> new SecurityConfig().users(environment)).isInstanceOf(IllegalStateException.class);
        }
    }
}
