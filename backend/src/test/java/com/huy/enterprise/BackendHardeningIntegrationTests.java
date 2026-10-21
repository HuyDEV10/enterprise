package com.huy.enterprise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huy.enterprise.audit.AuditLogRepository;
import com.huy.enterprise.common.enums.RecordStatus;
import com.huy.enterprise.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.auth.login.max-failures=2")
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "hardening-admin", roles = "ADMIN")
class BackendHardeningIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired AuditLogRepository audits;
    private UUID adminId;

    @BeforeEach
    void prepareOneActiveAdmin() {
        // This isolation is rolled back with the test, including any development users.
        users.findAll().forEach(user -> user.setStatus(RecordStatus.INACTIVE));
        users.flush();
        User admin = user("hardening-admin", "ADMIN");
        adminId = admin.getId();
    }

    @Test
    void lastAdminCannotBeDisabledOrDemoted() throws Exception {
        mvc.perform(patch("/api/users/{id}/status", adminId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/users/{id}/roles", adminId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"STAFF\"]}")).andExpect(status().isConflict());
        assertThat(users.countAdminsByStatus(RecordStatus.ACTIVE)).isEqualTo(1);
    }

    @Test
    void adminCanBeDisabledWhenAnotherActiveAdminRemainsAndChangeIsAudited() throws Exception {
        user("hardening-second-admin", "ADMIN");
        mvc.perform(patch("/api/users/{id}/status", adminId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}")).andExpect(status().isOk());
        assertThat(users.countAdminsByStatus(RecordStatus.ACTIVE)).isEqualTo(1);
        assertThat(audits.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("USER_STATUS_CHANGED");
            assertThat(log.getEntityId()).isEqualTo(adminId);
            assertThat(log.getActorUser().getId()).isEqualTo(adminId);
        });
    }

    @Test
    void malformedJsonInvalidEnumAndMissingParametersAre400WithCorrelation() throws Exception {
        mvc.perform(post("/api/auth/login").header("X-Request-ID", "regression-400")
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId").value("regression-400"))
                .andExpect(header().string("X-Request-ID", "regression-400"));
        mvc.perform(patch("/api/purchase-orders/{id}/status", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/ai/external-events")).andExpect(status().isBadRequest());
    }

    @Test
    void roleChangesRemainMutableAndAreAudited() throws Exception {
        user("hardening-second-admin", "ADMIN");
        mvc.perform(put("/api/users/{id}/roles", adminId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"STAFF\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("STAFF"));
        mvc.perform(put("/api/users/{id}/roles", adminId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ADMIN\",\"ANALYST\"]}")).andExpect(status().isOk());
        assertThat(users.findById(adminId).orElseThrow().getRoles()).hasSize(2);
    }

    @Test
    void nullOrBlankRoleNamesAreRejectedAsRequestErrors() throws Exception {
        for (String body : new String[]{"{\"roles\":[null]}", "{\"roles\":[\"  \"]}"}) {
            mvc.perform(put("/api/users/{id}/roles", adminId).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void ordersCannotSkipReopenOrEditCommittedStages() throws Exception {
        UUID id = draftOrder();
        changeOrderStatus(id, "DELIVERED", 409);
        changeOrderStatus(id, "ORDERED", 200);
        mvc.perform(put("/api/purchase-orders/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(orderUpdate)).andExpect(status().isConflict());
        changeOrderStatus(id, "IN_TRANSIT", 200);
        changeOrderStatus(id, "DELIVERED", 200);
        changeOrderStatus(id, "DRAFT", 409);
        mvc.perform(put("/api/purchase-orders/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(orderUpdate)).andExpect(status().isConflict());
        assertThat(audits.findAll().stream().filter(log -> id.equals(log.getEntityId()))
                .filter(log -> log.getAction().equals("PURCHASE_ORDER_STATUS_CHANGED"))).hasSize(3);
    }

    @Test
    void shipmentLifecycleProtectsOrderAndRecordsArrival() throws Exception {
        UUID orderId = draftOrder();
        String request = json.writeValueAsString(Map.of("shipmentCode", "HARDENING-SHIP", "purchaseOrderId", orderId));
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
        changeOrderStatus(orderId, "ORDERED", 200);
        UUID shipmentId = createdId("/api/shipments", request);
        changeShipmentStatus(shipmentId, "ARRIVED", 409);
        changeShipmentStatus(shipmentId, "SHIPPING", 200);
        changeOrderStatus(orderId, "DELIVERED", 409);
        mvc.perform(patch("/api/shipments/{id}/status", shipmentId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARRIVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actualArrivalDate").isNotEmpty());
        changeOrderStatus(orderId, "DELIVERED", 200);
        changeShipmentStatus(shipmentId, "SHIPPING", 409);
        mvc.perform(put("/api/shipments/{id}", shipmentId).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotCreateOrdersAlreadyDeliveredOrWithReversedDates() throws Exception {
        draftOrder();
        var request = json.readTree(orderUpdate).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) request).put("orderCode", "HARDENING-INVALID").put("status", "DELIVERED");
        mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest());
        ((com.fasterxml.jackson.databind.node.ObjectNode) request).put("status", "DRAFT")
                .put("expectedDeliveryDate", LocalDate.now().minusDays(1).toString());
        mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginFailuresAreThrottledWithRetryAfter() throws Exception {
        String body = "{\"username\":\"missing-hardening-account\",\"password\":\"invalid-password\"}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"));
    }

    @Test
    void paginatedReadsAreBoundedAndRejectUnsupportedSortFields() throws Exception {
        draftOrder();
        mvc.perform(get("/api/users/page").param("size", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content").isArray()).andExpect(jsonPath("$.totalElements").isNumber());
        mvc.perform(get("/api/users/page").param("sort", "passwordHash,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/purchase-orders/page").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].items").isArray());
        mvc.perform(get("/api/suppliers/page").param("riskLevel", "LOW").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].riskLevel").value("LOW"));
    }

    private String orderUpdate;

    private UUID draftOrder() throws Exception {
        UUID supplier = createdId("/api/suppliers", "{\"supplierCode\":\"HARDENING-SUP\",\"name\":\"Supplier\"}");
        UUID product = createdId("/api/products", "{\"productCode\":\"HARDENING-PROD\",\"name\":\"Product\",\"unit\":\"pcs\"}");
        orderUpdate = json.writeValueAsString(Map.of("supplierId", supplier, "orderDate", LocalDate.now().toString(),
                "items", java.util.List.of(Map.of("productId", product, "quantity", 2, "unitPrice", 100))));
        var create = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(orderUpdate);
        create.put("orderCode", "HARDENING-PO");
        return createdId("/api/purchase-orders", create.toString());
    }

    private UUID createdId(String path, String body) throws Exception {
        var response = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).path("id").asText());
    }

    private void changeOrderStatus(UUID id, String next, int expected) throws Exception {
        mvc.perform(patch("/api/purchase-orders/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", next)))).andExpect(status().is(expected));
    }

    private void changeShipmentStatus(UUID id, String next, int expected) throws Exception {
        mvc.perform(patch("/api/shipments/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", next)))).andExpect(status().is(expected));
    }

    private User user(String username, String role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setFullName(username);
        user.setPasswordHash("unused-test-hash");
        user.setRoles(Set.of(roles.findByName(role).orElseThrow()));
        return users.saveAndFlush(user);
    }
}
