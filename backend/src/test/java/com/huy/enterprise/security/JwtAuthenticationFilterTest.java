package com.huy.enterprise.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void storageFailureDoesNotMasqueradeAsAnInvalidSession() throws Exception {
        var jwt = mock(JwtService.class);
        var users = mock(CustomUserDetailsService.class);
        var chain = mock(FilterChain.class);
        var mapper = new ObjectMapper();
        var filter = new JwtAuthenticationFilter(jwt, users, mapper);
        var request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer technical-fixture");
        var response = new MockHttpServletResponse();
        when(jwt.extractUsername("technical-fixture")).thenReturn("user");
        when(users.loadUserByUsername("user")).thenThrow(new DataAccessResourceFailureException("technical database outage"));
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(mapper.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("AUTH_BACKEND_UNAVAILABLE");
        verifyNoInteractions(chain);
    }
}
