package com.huy.enterprise.auth;
public record LoginResponse(String accessToken, String tokenType, CurrentUserResponse user) {}
