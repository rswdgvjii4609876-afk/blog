package com.example.blog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/** Local-only exam workflow: no login required, but state-changing requests still require CSRF. */
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; " +
                "connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));
        // CSRF, frame denial, nosniff and secure session defaults remain enabled.
        return http.build();
    }

    @Bean
    UserDetailsService localUsers() { return new InMemoryUserDetailsManager(); }
}
