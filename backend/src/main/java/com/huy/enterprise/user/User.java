package com.huy.enterprise.user;

import com.huy.enterprise.common.BaseEntity;
import com.huy.enterprise.common.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;

@Getter @Setter @Entity @Table(name = "users")
public class User extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100) private String username;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "full_name", nullable = false) private String fullName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RecordStatus status = RecordStatus.ACTIVE;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();
}
