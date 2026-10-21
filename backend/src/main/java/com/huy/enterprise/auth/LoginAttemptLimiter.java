package com.huy.enterprise.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded per-instance failure windows. Share the counters at the gateway when scaling out. */
@Component
public class LoginAttemptLimiter {
    private static final int MAX_BUCKETS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final int maxFailures;
    private final long windowMs;
    private final Clock clock;
    private long lastCleanup;

    @Autowired
    public LoginAttemptLimiter(@Value("${app.auth.login.max-failures:5}") int maxFailures,
                               @Value("${app.auth.login.window-ms:60000}") long windowMs) {
        this(maxFailures, windowMs, Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxFailures, long windowMs, Clock clock) {
        if (maxFailures < 1 || windowMs < 1000) throw new IllegalStateException("Login rate-limit settings must be positive");
        this.maxFailures = maxFailures;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    public synchronized void check(String username, String address) {
        long now = clock.millis();
        cleanup(now);
        if (windows.size() >= MAX_BUCKETS && keys(username, address).stream().anyMatch(key -> !windows.containsKey(key))) {
            throw new LoginRateLimitException((windowMs + 999) / 1000);
        }
        for (String key : keys(username, address)) {
            Window window = windows.get(key);
            if (window != null && now - window.startedAt < windowMs && window.failures >= maxFailures) {
                throw new LoginRateLimitException(Math.max(1, (windowMs - (now - window.startedAt) + 999) / 1000));
            }
        }
    }

    public synchronized void failed(String username, String address) {
        long now = clock.millis();
        cleanup(now);
        for (String key : keys(username, address)) {
            Window window = windows.get(key);
            if (window == null || now - window.startedAt >= windowMs) {
                if (windows.size() >= MAX_BUCKETS) throw new LoginRateLimitException((windowMs + 999) / 1000);
                window = new Window(now);
                windows.put(key, window);
            }
            window.failures++;
        }
    }

    public synchronized void succeeded(String username) {
        windows.remove(accountKey(username));
    }

    private List<String> keys(String username, String address) {
        return List.of(accountKey(username), "ip:" + address);
    }

    private String accountKey(String username) { return "account:" + username.trim().toLowerCase(Locale.ROOT); }

    private void cleanup(long now) {
        if (now - lastCleanup >= windowMs || windows.size() >= MAX_BUCKETS) {
            windows.values().removeIf(window -> now - window.startedAt >= windowMs);
            lastCleanup = now;
        }
    }

    private static class Window {
        final long startedAt;
        int failures;
        Window(long startedAt) { this.startedAt = startedAt; }
    }
}
