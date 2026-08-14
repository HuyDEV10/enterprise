package com.huy.enterprise;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase2ApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode postJson(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void supplierCrudValidationConflictAndNotFound() throws Exception {
        String body = """
          {"supplierCode":"SUP-T1","name":"Supplier Test","email":"test@example.com","riskLevel":"LOW"}
          """;
        JsonNode created = postJson("/api/suppliers", body);
        String id = created.get("id").asText();

        mvc.perform(get("/api/suppliers")).andExpect(status().isOk());
        mvc.perform(put("/api/suppliers/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Supplier Updated\",\"email\":\"updated@example.com\",\"riskLevel\":\"HIGH\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Supplier Updated"));
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierCode\":\"\",\"name\":\"\",\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/suppliers/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(delete("/api/suppliers/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/suppliers/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void supplyChainRiskFlowWorksEndToEnd() throws Exception {
        JsonNode supplier = postJson("/api/suppliers", """
          {"supplierCode":"SUP-E2E","name":"E2E Supplier","email":"e2e@example.com","riskLevel":"HIGH"}
          """);
        String supplierId = supplier.get("id").asText();

        JsonNode product = postJson("/api/products", """
          {"productCode":"PROD-E2E","name":"E2E Product","unit":"pcs","referencePrice":100.00}
          """);
        String productId = product.get("id").asText();
        mvc.perform(put("/api/products/" + productId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"E2E Product Updated\",\"unit\":\"pcs\",\"referencePrice\":120.00}"))
                .andExpect(status().isOk());

        JsonNode warehouse = postJson("/api/warehouses", """
          {"warehouseCode":"WH-E2E","name":"E2E Warehouse","address":"Da Nang"}
          """);
        String warehouseId = warehouse.get("id").asText();

        JsonNode inventory = postJson("/api/inventory", """
          {"warehouseId":"%s","productId":"%s","quantity":10,"lowStockThreshold":10}
          """.formatted(warehouseId, productId));
        String inventoryId = inventory.get("id").asText();
        mvc.perform(get("/api/inventory/low-stock")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + inventoryId + "')]").exists());

        mvc.perform(post("/api/purchase-orders").contentType(MediaType.APPLICATION_JSON).content("""
          {"orderCode":"BAD-ORDER","supplierId":"00000000-0000-0000-0000-000000000001","orderDate":"2026-08-14","items":[{"productId":"%s","quantity":1,"unitPrice":10}]}
          """.formatted(productId))).andExpect(status().isNotFound());

        JsonNode order = postJson("/api/purchase-orders", """
          {"orderCode":"PO-E2E","supplierId":"%s","orderDate":"2026-08-14","expectedDeliveryDate":"2026-08-20","items":[{"productId":"%s","quantity":2,"unitPrice":120.00}]}
          """.formatted(supplierId, productId));
        String orderId = order.get("id").asText();
        mvc.perform(get("/api/purchase-orders/" + orderId)).andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(240.0));
        mvc.perform(patch("/api/purchase-orders/" + orderId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ORDERED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ORDERED"));

        JsonNode shipment = postJson("/api/shipments", """
          {"shipmentCode":"SHIP-E2E","purchaseOrderId":"%s","carrierName":"E2E Carrier","departureDate":"2026-08-15","expectedArrivalDate":"2026-08-20"}
          """.formatted(orderId));
        String shipmentId = shipment.get("id").asText();
        mvc.perform(patch("/api/shipments/" + shipmentId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SHIPPING\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPING"));

        JsonNode risk = postJson("/api/risk-events", """
          {"title":"Supplier delay risk","riskType":"SUPPLIER","impactLevel":"HIGH","description":"E2E risk","supplierId":"%s","productId":"%s","purchaseOrderId":"%s","shipmentId":"%s"}
          """.formatted(supplierId, productId, orderId, shipmentId));
        String riskId = risk.get("id").asText();
        mvc.perform(patch("/api/risk-events/" + riskId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INVESTIGATING\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INVESTIGATING"));

        JsonNode alert = postJson("/api/alerts", """
          {"title":"Risk alert","message":"Supplier delay detected","alertType":"RISK","priority":"HIGH","riskEventId":"%s"}
          """.formatted(riskId));
        String alertId = alert.get("id").asText();
        mvc.perform(patch("/api/alerts/" + alertId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"READ\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/alerts/" + alertId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        mvc.perform(get("/api/dashboard/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSuppliers").isNumber())
                .andExpect(jsonPath("$.highRiskSuppliers").isNumber())
                .andExpect(jsonPath("$.totalPurchaseOrders").isNumber())
                .andExpect(jsonPath("$.lowStockItems").isNumber());
    }

    @Test
    void companyDepartmentEmployeeAndCategoryApisAreReachable() throws Exception {
        mvc.perform(get("/api/companies")).andExpect(status().isOk());
        mvc.perform(get("/api/departments")).andExpect(status().isOk());
        mvc.perform(get("/api/employees")).andExpect(status().isOk());
        mvc.perform(get("/api/product-categories")).andExpect(status().isOk());
    }
}
