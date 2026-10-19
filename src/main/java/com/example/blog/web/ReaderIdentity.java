package com.example.blog.web;

import jakarta.servlet.http.*;
import org.springframework.http.ResponseCookie;
import java.time.Duration;
import java.util.UUID;

final class ReaderIdentity {
    private static final String COOKIE = "reader_id";
    private ReaderIdentity() { }
    static String read(HttpServletRequest request) {
        if (request.getCookies() != null) for (var cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return cookie.getValue();
        }
        return null;
    }
    static String ensure(HttpServletRequest request, HttpServletResponse response) {
        String id = read(request);
        if (id == null) {
            id = UUID.randomUUID().toString();
            response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, id).path("/").httpOnly(true)
                .secure(request.isSecure()).sameSite("Lax").maxAge(Duration.ofDays(365)).build().toString());
        }
        return id;
    }
}
