package com.huy.enterprise.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured with a unique Base64-encoded signing key");
        }
        try {
            this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64 containing at least 32 random bytes");
        }
        if (expirationMs <= 0) {
            throw new IllegalStateException("JWT expiration must be positive");
        }
        this.expirationMs = expirationMs;
    }
    public String generateToken(UserDetails user) {
        List<String> roles = user.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        Date now = new Date();
        return Jwts.builder().subject(user.getUsername()).claim("roles", roles).issuedAt(now).expiration(new Date(now.getTime() + expirationMs)).signWith(key).compact();
    }
    public String extractUsername(String token) { return claims(token).getSubject(); }
    public boolean isValid(String token, UserDetails user) { return user.getUsername().equals(extractUsername(token)) && claims(token).getExpiration().after(new Date()); }
    private Claims claims(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}
