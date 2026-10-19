package com.example.blog.web;

import com.example.blog.config.SecurityConfig;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class SessionView {
    private final Environment environment;
    public SessionView(Environment environment) { this.environment = environment; }

    @ModelAttribute("adminAuthenticated")
    public boolean authenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @ModelAttribute("adminLoginEnabled")
    public boolean loginEnabled() { return SecurityConfig.loginRequired(environment); }
}
