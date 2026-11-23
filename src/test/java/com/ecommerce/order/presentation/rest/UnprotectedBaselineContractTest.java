package com.ecommerce.order.presentation.rest;

import com.ecommerce.order.domain.port.out.PaymentPort;
import com.ecommerce.order.infrastructure.resilience.UnprotectedPaymentAdapter;
import com.ecommerce.order.infrastructure.resilience.gateway.ExternalPaymentClient;
import com.ecommerce.order.infrastructure.resilience.gateway.PaymentGatewayException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The unmitigated baseline used in docs/quality/measurement-evidence.md ({@code payment.resilience.enabled=false}):
 * a gateway failure reaches the customer as HTTP 500 and the order stays PLACED.
 */
@SpringBootTest(properties = {"payment.resilience.enabled=false", "checkout.rate-limit.enabled=false"})
@AutoConfigureMockMvc
class UnprotectedBaselineContractTest {

    private static final String ORDER = """
            {"customerId":"CUST-1",
             "shippingAddress":{"street":"Peace Ave 1","city":"Ulaanbaatar","postalCode":"14200","countryCode":"MN"},
             "items":[{"sku":"ELEC-1001","unitPrice":25.50,"currency":"USD","quantity":2}]}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentPort paymentPort;

    @MockitoBean
    private ExternalPaymentClient externalClient;

    @Test
    @DisplayName("Without the circuit breaker a gateway timeout becomes a 500 and nothing is deferred")
    void gatewayFailureBecomes500() throws Exception {
        assertThat(paymentPort).isInstanceOf(UnprotectedPaymentAdapter.class);
        given(externalClient.callRemoteGateway(any(), any())).willThrow(new PaymentGatewayException("Timeout"));
        String body = mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.orderId");

        mockMvc.perform(put("/api/v1/orders/{id}/payment", id)).andExpect(status().isInternalServerError());
        mockMvc.perform(get("/api/v1/orders/{id}", id)).andExpect(jsonPath("$.status").value("PLACED"));
    }
}
