package com.huy.enterprise.auth;

import com.huy.enterprise.security.JwtService;
import com.huy.enterprise.user.User;
import com.huy.enterprise.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    @Transactional(readOnly = true) public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        return new LoginResponse(jwtService.generateToken(principal), "Bearer", CurrentUserResponse.from(user));
    }
    @Transactional(readOnly = true) public CurrentUserResponse currentUser(String username) { return CurrentUserResponse.from(userRepository.findByUsername(username).orElseThrow()); }
}
