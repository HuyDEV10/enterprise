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
    private final String secret;
    private final long expirationMs;
    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-ms}") long expirationMs) { this.secret = secret; this.expirationMs = expirationMs; }
    public String generateToken(UserDetails user) {
        List<String> roles = user.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        Date now = new Date();
        return Jwts.builder().subject(user.getUsername()).claim("roles", roles).issuedAt(now).expiration(new Date(now.getTime() + expirationMs)).signWith(signingKey()).compact();
    }
    public String extractUsername(String token) { return claims(token).getSubject(); }
    public boolean isValid(String token, UserDetails user) { return user.getUsername().equals(extractUsername(token)) && claims(token).getExpiration().after(new Date()); }
    private Claims claims(String token) { return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload(); }
    private SecretKey signingKey() { return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)); }
}
