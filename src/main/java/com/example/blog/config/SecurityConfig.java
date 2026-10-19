package com.example.blog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SecurityConfig {
    public static boolean loginRequired(Environment environment) {
        // An external bind must never turn a local no-login demo into an unprotected server.
        boolean exposed = !java.util.Set.of("127.0.0.1", "localhost", "::1", "[::1]")
            .contains(environment.getProperty("server.address", "127.0.0.1"));
        return exposed || environment.acceptsProfiles(Profiles.of("public", "shared"))
            || environment.getProperty("blog.admin.enabled", Boolean.class, false);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, Environment environment) throws Exception {
        if (loginRequired(environment)) {
            http.addFilterBefore(new LoginThrottleFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
            http.authorizeHttpRequests(auth -> auth.requestMatchers("/admin", "/admin/**").authenticated()
                    .anyRequest().permitAll())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/admin/posts", true)
                    .failureUrl("/login?error").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/?signedout").deleteCookies("JSESSIONID"));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(form -> form.disable());
        }
        http.httpBasic(basic -> basic.disable())
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; " +
                "connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));
        return http.build();
    }

    @Bean
    public UserDetailsService users(Environment environment) {
        if (!loginRequired(environment)) return new InMemoryUserDetailsManager();
        String password = environment.getProperty("blog.admin.password", "");
        String username = environment.getProperty("blog.admin.username", "admin");
        if (password.length() < 16 || password.length() > 64 || password.isBlank()
            || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Network/shared/public mode requires BLOG_ADMIN_PASSWORD (16-64 characters). No default password is supplied.");
        }
        if (!username.matches("[A-Za-z0-9_.-]{3,64}")) {
            throw new IllegalStateException("BLOG_ADMIN_USERNAME must contain 3-64 letters, digits, dots, underscores or hyphens.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
            .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password)).roles("ADMIN").build());
    }
}
