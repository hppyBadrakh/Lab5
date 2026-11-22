package com.ecommerce.order.infrastructure.messaging;

import com.ecommerce.order.domain.event.OrderPlacedDomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the two promises of the side-channel against the real wiring, with the downstream sender replaced:
 * a hanging sender does not delay the {@code 201 Created}, and a failing sender does not fail it.
 * Neither test measures time, so they do not flake on a slow machine.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SideChannelIsolationIntegrationTest {

    private static final String BODY = """
            {"customerId":"CUST-SIDE",
             "shippingAddress":{"street":"Peace Ave 1","city":"Ulaanbaatar","postalCode":"14200","countryCode":"MN"},
             "items":[{"sku":"ELEC-1001","unitPrice":12.50,"currency":"USD","quantity":2}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderNotificationSender notificationSender;

    @Test
    @DisplayName("201 Created is returned while the downstream sender is still hanging on a side-channel thread")
    void shouldRespondWhileSideChannelIsStillBusy() throws Exception {
        CountDownLatch senderEntered = new CountDownLatch(1);
        CountDownLatch releaseSender = new CountDownLatch(1);
        AtomicReference<String> senderThread = new AtomicReference<>();
        doAnswer(invocation -> {
            senderThread.set(Thread.currentThread().getName());
            senderEntered.countDown();
            releaseSender.await(30, TimeUnit.SECONDS);
            return null;
        }).when(notificationSender).sendOrderConfirmation(any(OrderPlacedDomainEvent.class));

        try {
            // If the listener ran on the request thread, this call would block until the latch timed out.
            mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PLACED"));

            assertThat(senderEntered.await(5, TimeUnit.SECONDS)).as("side-channel was triggered").isTrue();
            assertThat(releaseSender.getCount()).as("sender is still hanging after the response").isEqualTo(1);
            assertThat(senderThread.get())
                    .startsWith(SideChannelConfiguration.THREAD_NAME_PREFIX)
                    .isNotEqualTo(Thread.currentThread().getName());
        } finally {
            releaseSender.countDown();
        }
    }

    @Test
    @DisplayName("A failing downstream sender neither fails the 201 Created nor loses the order")
    void shouldKeepOrderWhenSideChannelFails() throws Exception {
        doThrow(new IllegalStateException("mail server is down"))
                .when(notificationSender).sendOrderConfirmation(any(OrderPlacedDomainEvent.class));

        String location = mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        verify(notificationSender, timeout(5000)).sendOrderConfirmation(any(OrderPlacedDomainEvent.class));
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"));
    }
}
