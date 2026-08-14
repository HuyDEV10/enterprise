package com.huy.enterprise.user;

import com.huy.enterprise.common.ConflictException;
import com.huy.enterprise.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class UserService {
    private final UserRepository users; private final RoleRepository roles; private final PasswordEncoder encoder;
    @Transactional(readOnly=true) public List<UserResponse> all(){ return users.findAll().stream().map(u -> UserResponse.from(users.findByUsername(u.getUsername()).orElseThrow())).toList(); }
    @Transactional(readOnly=true) public UserResponse one(UUID id){ return UserResponse.from(withRoles(id)); }
    @Transactional public UserResponse create(CreateUserRequest r){
        if(users.existsByUsername(r.username())) throw new ConflictException("Username already exists");
        if(users.existsByEmail(r.email())) throw new ConflictException("Email already exists");
        User u=new User(); u.setUsername(r.username().trim()); u.setEmail(r.email().trim()); u.setFullName(r.fullName().trim()); u.setPasswordHash(encoder.encode(r.password())); u.setRoles(resolveRoles(r.roles())); return UserResponse.from(users.save(u));
    }
    @Transactional public UserResponse update(UUID id, UpdateUserRequest r){ User u=withRoles(id); if(!u.getEmail().equalsIgnoreCase(r.email()) && users.existsByEmail(r.email())) throw new ConflictException("Email already exists"); u.setEmail(r.email().trim()); u.setFullName(r.fullName().trim()); return UserResponse.from(users.save(u)); }
    @Transactional public UserResponse status(UUID id, UpdateUserStatusRequest r){ User u=withRoles(id); u.setStatus(r.status()); return UserResponse.from(users.save(u)); }
    @Transactional public UserResponse roles(UUID id, UpdateUserRolesRequest r){ User u=withRoles(id); u.setRoles(resolveRoles(r.roles())); return UserResponse.from(users.save(u)); }
    @Transactional(readOnly=true) public List<Role> allRoles(){ return roles.findAll(); }
    private User withRoles(UUID id){ User base=users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); return users.findByUsername(base.getUsername()).orElseThrow(() -> new ResourceNotFoundException("User not found")); }
    private Set<Role> resolveRoles(Set<String> names){ Set<String> normalized=names.stream().map(String::trim).map(String::toUpperCase).collect(Collectors.toSet()); List<Role> found=roles.findAllByNameIn(normalized); if(found.size()!=normalized.size()) throw new ResourceNotFoundException("One or more roles do not exist"); return Set.copyOf(found); }
}
