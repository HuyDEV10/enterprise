package com.huy.enterprise.user;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
public record UpdateUserRolesRequest(@NotEmpty Set<@NotBlank String> roles) {}
