package com.ecommerce.order.infrastructure.resilience;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.PaymentResult;
import com.ecommerce.order.domain.model.PaymentResult.PaymentStatus;
import com.ecommerce.order.infrastructure.resilience.gateway.ExternalPaymentClient;
import com.ecommerce.order.infrastructure.resilience.gateway.PaymentGatewayException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Scenario S2 (Availability): the circuit breaker around the PaymentPort trips on gateway failures,
 * short-circuits in under 5 ms without touching the network, and keeps the customer's intent.
 * Uses the real Spring AOP proxy and the configuration in application.properties.
 */
@SpringBootTest(properties = "checkout.rate-limit.enabled=false")
class ResilientPaymentAdapterTest {

    private static final Money AMOUNT = Money.of("150.00", "USD");

    @Autowired
    private ResilientPaymentAdapter paymentAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private DeferredPaymentStore deferredPayments;

    @MockitoBean
    private ExternalPaymentClient externalClient;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void resetCircuit() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker(ResilientPaymentAdapter.CIRCUIT_BREAKER);
        circuitBreaker.reset();
    }

    private static OrderId newOrderId() {
        return OrderId.generate();
    }

    @Test
    @DisplayName("Invariant 1: a healthy gateway charges the order and the circuit stays CLOSED")
    void healthyGateway_circuitStaysClosed() {
        OrderId orderId = newOrderId();
        given(externalClient.callRemoteGateway(any(), any()))
                .willReturn(new PaymentResult("TX-1", orderId, PaymentStatus.SUCCESS, "Charged"));

        PaymentResult result = paymentAdapter.processPayment(orderId, AMOUNT);

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.reference()).isEqualTo("TX-1");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Invariant 2: repeated gateway timeouts trip the circuit from CLOSED to OPEN; every caller gets a deferred result")
    void repeatedTimeouts_tripCircuitToOpen() {
        given(externalClient.callRemoteGateway(any(), any())).willThrow(new PaymentGatewayException("Timeout"));
        int storedBefore = deferredPayments.count();

        for (int i = 0; i < 5; i++) {
            PaymentResult result = paymentAdapter.processPayment(newOrderId(), AMOUNT);
            assertThat(result.status()).isEqualTo(PaymentStatus.DEFERRED_PENDING);
            assertThat(result.reference()).startsWith("DEFERRED-");
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(100.0f);
        assertThat(deferredPayments.count() - storedBefore).as("no checkout intent is dropped").isEqualTo(5);
        verify(externalClient, times(5)).callRemoteGateway(any(), any());
    }

    @Test
    @DisplayName("Invariant 3: an OPEN circuit short-circuits in under 5 ms (p95) without calling the gateway")
    void openCircuit_shortCircuitsWithoutCallingRemoteClient() {
        circuitBreaker.transitionToOpenState();
        for (int i = 0; i < 20; i++) {
            paymentAdapter.processPayment(newOrderId(), AMOUNT); // JIT warm-up
        }

        long[] nanos = new long[200];
        for (int i = 0; i < nanos.length; i++) {
            long start = System.nanoTime();
            PaymentResult result = paymentAdapter.processPayment(newOrderId(), AMOUNT);
            nanos[i] = System.nanoTime() - start;
            assertThat(result.status()).isEqualTo(PaymentStatus.DEFERRED_PENDING);
        }

        Arrays.sort(nanos);
        double p95Millis = nanos[(int) (nanos.length * 0.95)] / 1_000_000.0;
        verifyNoInteractions(externalClient);
        assertThat(p95Millis).as("p95 fallback latency in ms").isLessThan(5.0);
        assertThat(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(220);
    }

    @Test
    @DisplayName("Invariant 4: after the wait period, successful trial calls in HALF_OPEN close the circuit again")
    void halfOpenCircuit_closesAfterSuccessfulTrials() {
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        given(externalClient.callRemoteGateway(any(), any()))
                .willAnswer(call -> new PaymentResult("TX-OK", call.getArgument(0), PaymentStatus.SUCCESS, "Charged"));

        for (int i = 0; i < 3; i++) {
            assertThat(paymentAdapter.processPayment(newOrderId(), AMOUNT).status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Bulkhead: with 25 calls already waiting on the gateway, the 26th is deferred at once and the circuit stays CLOSED")
    void fullBulkhead_defersWithoutCallingTheGateway() throws Exception {
        CountDownLatch gatewayHangs = new CountDownLatch(1);
        CountDownLatch allWaiting = new CountDownLatch(25);
        given(externalClient.callRemoteGateway(any(), any())).willAnswer(call -> {
            allWaiting.countDown();
            gatewayHangs.await(10, TimeUnit.SECONDS);
            return new PaymentResult("TX-SLOW", call.getArgument(0), PaymentStatus.SUCCESS, "Charged");
        });
        List<Thread> waiting = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            waiting.add(Thread.ofVirtual().start(() -> paymentAdapter.processPayment(newOrderId(), AMOUNT)));
        }
        assertThat(allWaiting.await(5, TimeUnit.SECONDS)).isTrue();

        long start = System.nanoTime();
        PaymentResult result = paymentAdapter.processPayment(newOrderId(), AMOUNT);
        double millis = (System.nanoTime() - start) / 1_000_000.0;

        gatewayHangs.countDown();
        for (Thread thread : waiting) {
            thread.join();
        }
        assertThat(result.status()).isEqualTo(PaymentStatus.DEFERRED_PENDING);
        assertThat(millis).isLessThan(50.0);
        verify(externalClient, times(25)).callRemoteGateway(any(), any());
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("A HALF_OPEN trial that fails opens the circuit again")
    void halfOpenCircuit_reopensOnFailure() {
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        given(externalClient.callRemoteGateway(any(), any())).willThrow(new PaymentGatewayException("still down"));

        for (int i = 0; i < 3; i++) {
            paymentAdapter.processPayment(newOrderId(), AMOUNT);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        clearInvocations(externalClient);
        paymentAdapter.processPayment(newOrderId(), AMOUNT);
        verifyNoInteractions(externalClient);
    }
}
