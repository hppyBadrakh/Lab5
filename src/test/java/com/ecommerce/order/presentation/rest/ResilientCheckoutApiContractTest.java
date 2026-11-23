package com.ecommerce.order.presentation.rest;

import com.ecommerce.order.domain.model.PaymentResult;
import com.ecommerce.order.domain.model.PaymentResult.PaymentStatus;
import com.ecommerce.order.infrastructure.resilience.ResilientPaymentAdapter;
import com.ecommerce.order.infrastructure.resilience.gateway.ExternalPaymentClient;
import com.ecommerce.order.infrastructure.resilience.gateway.PaymentGatewayException;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario S2 end to end through MockMvc: the whole stack (controller, use case, aggregate, resilient adapter)
 * with only the remote gateway replaced. A gateway outage turns into 202 Accepted, never into a 5xx.
 */
@SpringBootTest(properties = "checkout.rate-limit.enabled=false")
@AutoConfigureMockMvc
class ResilientCheckoutApiContractTest {

    private static final String ORDER = """
            {"customerId":"CUST-1",
             "shippingAddress":{"street":"Peace Ave 1","city":"Ulaanbaatar","postalCode":"14200","countryCode":"MN"},
             "items":[{"sku":"ELEC-1001","unitPrice":25.50,"currency":"USD","quantity":2}]}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private ExternalPaymentClient externalClient;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void resetCircuit() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker(ResilientPaymentAdapter.CIRCUIT_BREAKER);
        circuitBreaker.reset();
    }

    private String placeOrder() throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.orderId");
    }

    @Test
    @DisplayName("Healthy gateway: PUT payment -> 204, order PAID")
    void healthyGatewayPaysTheOrder() throws Exception {
        given(externalClient.callRemoteGateway(any(), any()))
                .willAnswer(call -> new PaymentResult("TX-1", call.getArgument(0), PaymentStatus.SUCCESS, "Charged"));
        String id = placeOrder();

        mockMvc.perform(put("/api/v1/orders/{id}/payment", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/orders/{id}", id)).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("Gateway timeout: PUT payment -> 202 Accepted, order PAYMENT_PENDING, no 5xx")
    void gatewayTimeoutDefersThePayment() throws Exception {
        given(externalClient.callRemoteGateway(any(), any())).willThrow(new PaymentGatewayException("Timeout"));
        String id = placeOrder();

        mockMvc.perform(put("/api/v1/orders/{id}/payment", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.paymentReference").value(org.hamcrest.Matchers.startsWith("DEFERRED-")));
        mockMvc.perform(get("/api/v1/orders/{id}", id)).andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));
    }

    @Test
    @DisplayName("OPEN circuit: PUT payment answers 202 in under 50 ms over HTTP without calling the gateway")
    void openCircuitAnswersFastOverHttp() throws Exception {
        String id = placeOrder();
        circuitBreaker.transitionToOpenState();
        mockMvc.perform(put("/api/v1/orders/{id}/payment", placeOrder())); // warm-up

        long start = System.nanoTime();
        mockMvc.perform(put("/api/v1/orders/{id}/payment", id)).andExpect(status().isAccepted());
        long millis = (System.nanoTime() - start) / 1_000_000;

        verifyNoInteractions(externalClient);
        assertThat(millis).isLessThan(50);
    }

    @Test
    @DisplayName("A deferred order is settled by paying again once the gateway is back: 202 then 204")
    void deferredPaymentCanBeRetried() throws Exception {
        String id = placeOrder();
        given(externalClient.callRemoteGateway(any(), any())).willThrow(new PaymentGatewayException("Timeout"));
        mockMvc.perform(put("/api/v1/orders/{id}/payment", id)).andExpect(status().isAccepted());

        willAnswer(call -> new PaymentResult("TX-2", call.getArgument(0), PaymentStatus.SUCCESS, "Charged"))
                .given(externalClient).callRemoteGateway(any(), any());
        mockMvc.perform(put("/api/v1/orders/{id}/payment", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/orders/{id}", id)).andExpect(jsonPath("$.status").value("PAID"));
    }
}
