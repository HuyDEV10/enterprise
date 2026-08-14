package com.huy.enterprise.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/users") @RequiredArgsConstructor
public class UserController {
    private final UserService service;
    @GetMapping public List<UserResponse> all(){ return service.all(); }
    @GetMapping("/{id}") public UserResponse one(@PathVariable UUID id){ return service.one(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public UserResponse create(@Valid @RequestBody CreateUserRequest request){ return service.create(request); }
    @PutMapping("/{id}") public UserResponse update(@PathVariable UUID id,@Valid @RequestBody UpdateUserRequest request){ return service.update(id,request); }
    @PatchMapping("/{id}/status") public UserResponse status(@PathVariable UUID id,@Valid @RequestBody UpdateUserStatusRequest request){ return service.status(id,request); }
    @PutMapping("/{id}/roles") public UserResponse roles(@PathVariable UUID id,@Valid @RequestBody UpdateUserRolesRequest request){ return service.roles(id,request); }
}
