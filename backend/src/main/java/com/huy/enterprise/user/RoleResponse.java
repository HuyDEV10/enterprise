package com.huy.enterprise.user;
import java.util.UUID;
public record RoleResponse(UUID id,String name,String description){ public static RoleResponse from(Role r){return new RoleResponse(r.getId(),r.getName(),r.getDescription());}}
