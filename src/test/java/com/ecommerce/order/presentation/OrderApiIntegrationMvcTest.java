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

/** Runs the real Spring wiring: controller, service and in-memory repository. */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiIntegrationMvcTest {

    private static final String ITEM = """
            {"productId":"%s","productName":"Item","unitPrice":12.50,"currency":"%s","quantity":2}""";

    @Autowired
    private MockMvc mockMvc;

    private String createOrder() throws Exception {
        String body = "{\"customerId\":\"CUST-1\",\"items\":[" + ITEM.formatted("P1", "USD") + "]}";
        return mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    @DisplayName("Create, read, cancel, cancel again: 201, 200, 200, 409")
    void shouldRunOrderLifecycle() throws Exception {
        String location = createOrder();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(25.00));
        mockMvc.perform(post(location + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post(location + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("CANCELLED")));
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Mixed currencies: 400 instead of a stored order that always fails with 500")
    void shouldRejectMixedCurrencies() throws Exception {
        String body = "{\"customerId\":\"CUST-1\",\"items\":["
                + ITEM.formatted("P1", "USD") + "," + ITEM.formatted("P2", "EUR") + "]}";

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("same currency")));
    }

    @Test
    @DisplayName("Client-supplied id and status are ignored (no over-posting)")
    void shouldIgnoreOverPostedFields() throws Exception {
        String body = "{\"customerId\":\"CUST-1\",\"orderId\":\"HACK\",\"status\":\"PAID\",\"items\":["
                + ITEM.formatted("P1", "USD") + "]}";

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
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
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/cancel']").exists());
    }
}
