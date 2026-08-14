package com.huy.enterprise.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(@NotBlank @Size(max=100) String username, @NotBlank @Email String email, @NotBlank @Size(min=8) String password, @NotBlank String fullName, @NotEmpty Set<String> roles) {}
