package com.example.blog.service;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Bounded per-IP fixed-window limit. Addresses are retained only in memory for one minute. */
@Component
public class ReaderWriteLimiter {
    private record Window(long start, int attempts) { }
    private final Map<String, Window> windows = new HashMap<>();
    private long nextCleanup;
    public synchronized void check(String address, boolean comment) {
        long now = System.nanoTime();
        long minute = 60_000_000_000L;
        if (nextCleanup == 0 || now - nextCleanup >= 0) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() >= minute);
            nextCleanup = now + minute;
        }
        String key = (comment ? "comment:" : "rating:") + address;
        var window = windows.get(key);
        if (window == null || now - window.start() >= minute) {
            if (window == null && windows.size() >= 10000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            windows.put(key, new Window(now, 1));
        } else {
            if (window.attempts() >= (comment ? 5 : 15)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            windows.put(key, new Window(window.start(), window.attempts() + 1));
        }
    }
}
