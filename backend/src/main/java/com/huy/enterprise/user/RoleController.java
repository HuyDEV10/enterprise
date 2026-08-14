package com.huy.enterprise.user;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/roles") @RequiredArgsConstructor
public class RoleController { private final RoleRepository roles; @GetMapping public List<RoleResponse> all(){return roles.findAll().stream().map(RoleResponse::from).toList();}}
