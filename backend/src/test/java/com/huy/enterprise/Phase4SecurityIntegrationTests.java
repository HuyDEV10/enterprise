package com.huy.enterprise;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huy.enterprise.common.enums.RecordStatus;
import com.huy.enterprise.user.Role;
import com.huy.enterprise.user.RoleRepository;
import com.huy.enterprise.user.User;
import com.huy.enterprise.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Phase4SecurityIntegrationTests {
    private static final String PASSWORD = "Test@123";
    private static final String ADMIN = "phase4_admin_test";
    private static final String STAFF = "phase4_staff_test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void prepareUsers() {
        createUser(ADMIN, "ADMIN");
        createUser(STAFF, "STAFF");
    }

    @Test
    void validLoginReturnsJwtAndCurrentUser() throws Exception {
        String token = login(ADMIN, PASSWORD);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN))
                .andExpect(jsonPath("$.roles[?(@ == 'ADMIN')]").exists());
    }

    @Test
    void invalidLoginReturns401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginBody(ADMIN, "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJwtReturns401() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanAccessUsersButStaffCannot() throws Exception {
        String adminToken = login(ADMIN, PASSWORD);
        String staffToken = login(STAFF, PASSWORD);

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody(username, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private void createUser(String username, String roleName) {
        users.findByUsername(username).ifPresent(users::delete);
        users.flush();
        Role role = roles.findByName(roleName).orElseThrow();
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.local");
        user.setFullName("Phase 4 " + roleName + " Test");
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setStatus(RecordStatus.ACTIVE);
        user.setRoles(Set.of(role));
        users.saveAndFlush(user);
    }

    private record LoginBody(String username, String password) {}
}
