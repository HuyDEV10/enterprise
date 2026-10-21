package com.huy.enterprise.security;

import com.huy.enterprise.common.enums.RecordStatus;
import com.huy.enterprise.user.User;
import com.huy.enterprise.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
        String[] authorities = user.getRoles().stream().map(r -> "ROLE_" + r.getName()).toArray(String[]::new);
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername()).password(user.getPasswordHash()).authorities(authorities).disabled(user.getStatus() != RecordStatus.ACTIVE).build();
    }
}
