package com.example.blog.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Single-instance, bounded login rate limit. Never retains usernames or passwords. */
public class LoginThrottleFilter extends OncePerRequestFilter {
    private final Map<String, Window> windows = new HashMap<>();
    private long nextCleanup;
    private record Window(long started, int attempts) { }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!allow(request.getRemoteAddr(), System.currentTimeMillis())) {
            response.setHeader("Retry-After", "60");
            response.sendError(429);
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String address, long now) {
        if (now >= nextCleanup) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().started() >= 60000);
            nextCleanup = now + 60000;
        }
        Window current = windows.get(address);
        if (current == null || now - current.started() >= 60000) {
            if (current == null && windows.size() >= 5000) return false;
            windows.put(address, new Window(now, 1));
            return true;
        }
        if (current.attempts() >= 10) return false;
        windows.put(address, new Window(current.started(), current.attempts() + 1));
        return true;
    }
}
