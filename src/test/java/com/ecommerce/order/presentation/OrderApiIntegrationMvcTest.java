package com.ecommerce.order.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the real Spring wiring: REST adapter, application service, aggregate and in-memory adapter. */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiIntegrationMvcTest {

    private static final String ITEM = """
            {"sku":"%s","unitPrice":%s,"currency":"%s","quantity":2}""";
    private static final String ADDRESS = """
            "shippingAddress":{"street":"Peace Ave 1","city":"Ulaanbaatar","postalCode":"14200","countryCode":"MN"}""";

    @Autowired
    private MockMvc mockMvc;

    private static String body(String... items) {
        return "{\"customerId\":\"CUST-1\"," + ADDRESS + ",\"items\":[" + String.join(",", items) + "]}";
    }

    private String placeOrder() throws Exception {
        return mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(ITEM.formatted("ELEC-1001", "12.50", "USD"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    @DisplayName("Place, read, pay, cancel, cancel again: 201, 200, 200, 200, 409")
    void shouldRunOrderLifecycle() throws Exception {
        String location = placeOrder();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.total").value(25.00));
        mockMvc.perform(post(location + "/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(post(location + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post(location + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("CANCELLED")));
        mockMvc.perform(post(location + "/pay"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Mixed currencies: 400 from the aggregate's currency invariant")
    void shouldRejectMixedCurrencies() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(ITEM.formatted("ELEC-1001", "12.50", "USD"), ITEM.formatted("ELEC-2002", "12.50", "EUR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Currency mismatch")));
    }

    @Test
    @DisplayName("Total above 10,000: 400 from the aggregate's limit invariant")
    void shouldRejectOrderAboveLimit() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(ITEM.formatted("ELEC-1001", "5000.01", "USD"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("limit of 10000.00 USD")));
    }

    @Test
    @DisplayName("Client-supplied id and status are ignored (no over-posting)")
    void shouldIgnoreOverPostedFields() throws Exception {
        String body = body(ITEM.formatted("ELEC-1001", "12.50", "USD"))
                .replace("{\"customerId\"", "{\"orderId\":\"HACK\",\"status\":\"PAID\",\"customerId\"");

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.orderId").value(not("HACK")));
    }

    @Test
    @DisplayName("Unknown route: 404 ProblemDetail, not 500")
    void shouldReturn404ForUnknownRoute() throws Exception {
        mockMvc.perform(get("/no/such/route"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("OpenAPI document describes the order endpoints")
    void shouldServeOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/orders']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/pay']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/cancel']").exists());
    }
}
