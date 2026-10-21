package com.huy.enterprise.auth;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAttemptLimiterTest {
    private final Clock clock = mock(Clock.class);
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, 60_000, clock);

    @Test
    void accountLimitAppliesAcrossAddressesAndExpires() {
        when(clock.millis()).thenReturn(100_000L);
        limiter.failed("admin", "192.0.2.1");
        limiter.failed("admin", "192.0.2.2");
        assertThatThrownBy(() -> limiter.check("ADMIN", "192.0.2.3")).isInstanceOf(LoginRateLimitException.class);
        when(clock.millis()).thenReturn(160_001L);
        assertThatCode(() -> limiter.check("admin", "192.0.2.1")).doesNotThrowAnyException();
    }

    @Test
    void addressLimitAppliesAcrossAccountsAndIsNotResetBySuccessfulLogin() {
        limiter.failed("first", "192.0.2.1");
        limiter.failed("second", "192.0.2.1");
        limiter.succeeded("second");
        assertThatThrownBy(() -> limiter.check("third", "192.0.2.1")).isInstanceOf(LoginRateLimitException.class);
    }

    @Test
    void successfulLoginClearsAccountFailures() {
        limiter.failed("admin", "192.0.2.1");
        limiter.succeeded("admin");
        limiter.failed("admin", "192.0.2.2");
        assertThatCode(() -> limiter.check("admin", "192.0.2.3")).doesNotThrowAnyException();
    }
}
