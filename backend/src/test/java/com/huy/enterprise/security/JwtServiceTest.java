package com.huy.enterprise.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    @Test
    void rejectsMissingMalformedAndWeakKeysAtStartup() {
        for (String key : new String[]{"", " ", "not-base64!", "d2Vhaw=="}) {
            assertThatThrownBy(() -> new JwtService(key, 60_000)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET");
        }
    }

    @Test
    void rejectsNonPositiveExpiration() {
        String key = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
        assertThatThrownBy(() -> new JwtService(key, 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void verifiesOwnTokensAndRejectsTokensSignedWithAnotherKey() {
        var first = new JwtService(Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded()), 60_000);
        var second = new JwtService(Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded()), 60_000);
        var user = User.withUsername("admin").password("unused-test-hash").roles("ADMIN").build();
        String token = first.generateToken(user);
        assertThat(first.isValid(token, user)).isTrue();
        assertThatThrownBy(() -> second.extractUsername(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredTokens() {
        var key = Jwts.SIG.HS256.key().build();
        var service = new JwtService(Encoders.BASE64.encode(key.getEncoded()), 60_000);
        String expired = Jwts.builder().subject("admin").expiration(new Date(1)).signWith(key).compact();
        assertThatThrownBy(() -> service.extractUsername(expired)).isInstanceOf(JwtException.class);
    }
}
