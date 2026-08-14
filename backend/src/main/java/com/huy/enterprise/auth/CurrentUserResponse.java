package com.huy.enterprise.auth;
import com.huy.enterprise.user.User;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
public record CurrentUserResponse(UUID id, String username, String email, String fullName, Set<String> roles) {
    public static CurrentUserResponse from(User user) { return new CurrentUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet())); }
}
