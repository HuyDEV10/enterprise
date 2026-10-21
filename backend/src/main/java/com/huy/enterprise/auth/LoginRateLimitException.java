package com.huy.enterprise.auth;

import lombok.Getter;

@Getter
public class LoginRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;
    public LoginRateLimitException(long retryAfterSeconds) {
        super("Too many failed login attempts; try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
