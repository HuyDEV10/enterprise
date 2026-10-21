package com.huy.enterprise.security;

import com.huy.enterprise.user.RoleRepository;
import com.huy.enterprise.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AdminBootstrap bootstrap = new AdminBootstrap(users, roles, encoder);

    @Test
    void disabledBootstrapDoesNotCreateAccounts() {
        bootstrap.run(new DefaultApplicationArguments());
        verifyNoInteractions(users, roles, encoder);
    }

    @Test
    void enabledBootstrapRequiresExplicitStrongPassword() {
        ReflectionTestUtils.setField(bootstrap, "enabled", true);
        for (String password : new String[]{null, "", "too-short", "é".repeat(40)}) {
            ReflectionTestUtils.setField(bootstrap, "password", password);
            assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
        }
        verifyNoInteractions(users, roles, encoder);
    }
}
