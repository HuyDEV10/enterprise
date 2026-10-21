package com.huy.enterprise.user;

import com.huy.enterprise.common.enums.RecordStatus;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(UUID id, String username, String email, String fullName, RecordStatus status, Set<String> roles) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getStatus(), user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
    }
}
