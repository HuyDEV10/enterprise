package com.huy.enterprise.security;

import com.huy.enterprise.common.enums.RecordStatus;
import com.huy.enterprise.user.Role;
import com.huy.enterprise.user.RoleRepository;
import com.huy.enterprise.user.User;
import com.huy.enterprise.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users; private final RoleRepository roles; private final PasswordEncoder encoder;
    @Value("${app.bootstrap-admin.enabled:false}") private boolean enabled;
    @Value("${app.bootstrap-admin.username:admin}") private String username;
    @Value("${app.bootstrap-admin.password:Admin@123}") private String password;
    @Value("${app.bootstrap-admin.email:admin@enterprise.local}") private String email;
    @Value("${app.bootstrap-admin.full-name:System Administrator}") private String fullName;
    public AdminBootstrap(UserRepository users, RoleRepository roles, PasswordEncoder encoder) { this.users = users; this.roles = roles; this.encoder = encoder; }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (!enabled || users.existsByUsername(username)) return;
        Role admin = roles.findByName("ADMIN").orElseThrow(() -> new IllegalStateException("ADMIN role is missing. Run Flyway migrations first."));
        User user = new User(); user.setUsername(username); user.setEmail(email); user.setFullName(fullName); user.setPasswordHash(encoder.encode(password)); user.setStatus(RecordStatus.ACTIVE); user.setRoles(Set.of(admin)); users.save(user);
    }
}
