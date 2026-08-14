package com.huy.enterprise;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.huy.enterprise.supplier.SupplierRepository;

@SpringBootTest
@AutoConfigureMockMvc
class Phase2ApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired SupplierRepository suppliers;

    @BeforeEach void clean() { suppliers.deleteAll(); }

    @Test void supplierCrudValidationConflictAndNotFound() throws Exception {
        String body="""
          {"supplierCode":"SUP-T1","name":"Supplier Test","email":"test@example.com","riskLevel":"LOW"}
          """;
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.supplierCode").value("SUP-T1"));
        mvc.perform(get("/api/suppliers")).andExpect(status().isOk()).andExpect(jsonPath("$[0].supplierCode").value("SUP-T1"));
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content("{\"supplierCode\":\"\",\"name\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/suppliers/00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test void dashboardAndCoreListsAreReachable() throws Exception {
        mvc.perform(get("/api/dashboard/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.totalSuppliers").exists());
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        mvc.perform(get("/api/warehouses")).andExpect(status().isOk());
        mvc.perform(get("/api/inventory")).andExpect(status().isOk());
        mvc.perform(get("/api/purchase-orders")).andExpect(status().isOk());
        mvc.perform(get("/api/shipments")).andExpect(status().isOk());
        mvc.perform(get("/api/risk-events")).andExpect(status().isOk());
        mvc.perform(get("/api/alerts")).andExpect(status().isOk());
    }
}
