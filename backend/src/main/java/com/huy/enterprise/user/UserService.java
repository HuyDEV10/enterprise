package com.huy.enterprise.user;

import org.springframework.data.domain.Pageable;
import com.huy.enterprise.common.PageResponse;
import com.huy.enterprise.common.PageRequests;

import com.huy.enterprise.audit.AuditService;
import com.huy.enterprise.common.ConflictException;
import com.huy.enterprise.common.ResourceNotFoundException;
import com.huy.enterprise.common.enums.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public List<UserResponse> all() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    public PageResponse<UserResponse> page(Pageable pageable) {
        var bounded = PageRequests.validate(pageable, "id", "username", "fullName", "email", "status", "createdAt", "updatedAt");
        return PageResponse.from(users.findAll(bounded).map(UserResponse::from));
    }

    public UserResponse one(UUID id) {
        return UserResponse.from(requireUser(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim();
        if (users.existsByUsername(username)) throw new ConflictException("Username already exists");
        if (users.existsByEmail(email)) throw new ConflictException("Email already exists");
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRoles(resolveRoles(request.roles()));
        user = users.save(user);
        audit.record("USER_CREATED", "USER", user.getId(), "Roles: " + roleNames(user.getRoles()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = requireUser(id);
        String email = request.email().trim();
        if (!user.getEmail().equals(email) && users.existsByEmail(email)) {
            throw new ConflictException("Email already exists");
        }
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        audit.record("USER_UPDATED", "USER", id, "Profile updated");
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse status(UUID id, UpdateUserStatusRequest request) {
        lockAdminChanges();
        User user = requireUser(id);
        if (isActiveAdmin(user) && request.status() != RecordStatus.ACTIVE) protectLastAdmin();
        RecordStatus previous = user.getStatus();
        user.setStatus(request.status());
        if (previous != request.status()) {
            audit.record("USER_STATUS_CHANGED", "USER", id, previous + " -> " + request.status());
        }
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse roles(UUID id, UpdateUserRolesRequest request) {
        lockAdminChanges();
        User user = requireUser(id);
        Set<Role> next = resolveRoles(request.roles());
        if (isActiveAdmin(user) && next.stream().noneMatch(r -> "ADMIN".equals(r.getName()))) protectLastAdmin();
        String previous = roleNames(user.getRoles());
        user.setRoles(next);
        audit.record("USER_ROLES_CHANGED", "USER", id, previous + " -> " + roleNames(next));
        return UserResponse.from(users.save(user));
    }

    public List<Role> allRoles() { return roles.findAll(); }

    private void lockAdminChanges() {
        // A shared database lock serializes administrative changes across instances.
        roles.lockAdminRole().orElseThrow(() -> new ResourceNotFoundException("ADMIN role not found"));
    }

    private void protectLastAdmin() {
        if (users.countAdminsByStatus(RecordStatus.ACTIVE) <= 1) {
            throw new ConflictException("At least one active ADMIN must remain");
        }
    }

    private boolean isActiveAdmin(User user) {
        return user.getStatus() == RecordStatus.ACTIVE
                && user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()));
    }

    private User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Set<Role> resolveRoles(Set<String> names) {
        Set<String> normalized = names.stream().map(String::trim)
                .map(name -> name.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        List<Role> found = roles.findAllByNameIn(normalized);
        if (found.size() != normalized.size()) throw new ResourceNotFoundException("One or more roles do not exist");
        return Set.copyOf(found);
    }

    private String roleNames(Set<Role> assigned) {
        return assigned.stream().map(Role::getName).sorted().collect(Collectors.joining(","));
    }
}
