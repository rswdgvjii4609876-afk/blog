package com.example.blog.web;

import com.example.blog.config.SecurityConfig;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
    private final Environment environment;
    public LoginController(Environment environment) { this.environment = environment; }
    @GetMapping("/login")
    public String login() { return SecurityConfig.loginRequired(environment) ? "login" : "redirect:/admin/overview"; }
}
