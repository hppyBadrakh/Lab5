package com.ecommerce.order.presentation.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario S3 (Scalability, flash surge): the checkout ingress sheds load above its permit budget with
 * 429 Too Many Requests. The budget is lowered to 3 permits per hour so the test is deterministic.
 */
@SpringBootTest(properties = {
        "resilience4j.ratelimiter.instances.checkout.limit-for-period=3",
        "resilience4j.ratelimiter.instances.checkout.limit-refresh-period=1h",
        "resilience4j.ratelimiter.instances.checkout.timeout-duration=0"})
@AutoConfigureMockMvc
class RateLimitedCheckoutContractTest {

    private static final String ORDER = """
            {"customerId":"CUST-SURGE",
             "shippingAddress":{"street":"Peace Ave 1","city":"Ulaanbaatar","postalCode":"14200","countryCode":"MN"},
             "items":[{"sku":"ELEC-1001","unitPrice":25.50,"currency":"USD","quantity":2}]}""";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v1/orders above the budget -> 429 ProblemDetail with Retry-After; reads are not limited")
    void shedsCheckoutLoadAboveTheBudget() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"));

        mockMvc.perform(get("/api/v1/customers/CUST-SURGE/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
