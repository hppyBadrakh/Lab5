package com.ecommerce.order.presentation.rest;

import com.ecommerce.order.infrastructure.messaging.OrderAuditAndNotificationSideChannelListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the real Spring wiring: REST adapter, application service, aggregate, in-memory adapter and side-channel. */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiIntegrationMvcTest {

    private static final String ITEM = """
            {"sku":"%s","unitPrice":%s,"currency":"%s","quantity":2}""";
    private static final String ADDRESS = """
            "shippingAddress":{"street":"Peace Ave 1","city":"Ulaanbaatar","postalCode":"14200","countryCode":"MN"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderAuditAndNotificationSideChannelListener sideChannel;

    private static String body(String customerId, String... items) {
        return "{\"customerId\":\"" + customerId + "\"," + ADDRESS + ",\"items\":[" + String.join(",", items) + "]}";
    }

    private String placeOrder(String customerId) throws Exception {
        return mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(customerId, ITEM.formatted("ELEC-1001", "12.50", "USD"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    private static String idOf(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("Place, read, pay, cancel, cancel again: 201, 200, 204, 204, 409")
    void shouldRunOrderLifecycle() throws Exception {
        String location = placeOrder("CUST-1");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.total").value(25.00));
        mockMvc.perform(put(location + "/payment"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(location))
                .andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(put(location + "/cancellation"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(location))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(put(location + "/cancellation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("State Transition Conflict"))
                .andExpect(jsonPath("$.detail").value(containsString("CANCELLED")));
        mockMvc.perform(put(location + "/payment"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A customer's order list holds only that customer's orders")
    void shouldListOrdersOfOneCustomer() throws Exception {
        String first = idOf(placeOrder("CUST-LIST-A"));
        String second = idOf(placeOrder("CUST-LIST-A"));
        placeOrder("CUST-LIST-B");

        mockMvc.perform(get("/api/v1/customers/CUST-LIST-A/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.orderId == '%s')]".formatted(first)).exists())
                .andExpect(jsonPath("$[?(@.orderId == '%s')]".formatted(second)).exists())
                .andExpect(jsonPath("$[0].total").value(25.00))
                .andExpect(jsonPath("$[0].itemCount").value(1));
        mockMvc.perform(get("/api/v1/customers/CUST-NOBODY/orders"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/api/v1/customers/" + "X".repeat(65) + "/orders"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Placing an order reaches the asynchronous side-channel through the domain event")
    void shouldAuditPlacedOrderOnTheSideChannel() throws Exception {
        String orderId = idOf(placeOrder("CUST-1"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(sideChannel.getRecordedAudits())
                        .anySatisfy(event -> {
                            assertThat(event.orderId().value()).isEqualTo(orderId);
                            assertThat(event.orderTotal().amount()).isEqualByComparingTo("25.00");
                        }));
    }

    @Test
    @DisplayName("Mixed currencies: 400 from the aggregate's currency invariant")
    void shouldRejectMixedCurrencies() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body("CUST-1", ITEM.formatted("ELEC-1001", "12.50", "USD"),
                                ITEM.formatted("ELEC-2002", "12.50", "EUR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Currency mismatch")));
    }

    @Test
    @DisplayName("Total above 10,000: 400 from the aggregate's limit invariant")
    void shouldRejectOrderAboveLimit() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body("CUST-1", ITEM.formatted("ELEC-1001", "5000.01", "USD"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("limit of 10000.00 USD")));
    }

    @Test
    @DisplayName("Client-supplied id and status are ignored (no over-posting)")
    void shouldIgnoreOverPostedFields() throws Exception {
        String body = body("CUST-1", ITEM.formatted("ELEC-1001", "12.50", "USD"))
                .replace("{\"customerId\"", "{\"orderId\":\"HACK\",\"status\":\"PAID\",\"customerId\"");

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.orderId").value(not("HACK")));
    }

    @Test
    @DisplayName("Unknown order and unknown route: 404 ProblemDetail, not 500")
    void shouldReturn404ForUnknownOrderAndRoute() throws Exception {
        mockMvc.perform(get("/api/v1/orders/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order Not Found"));
        mockMvc.perform(get("/no/such/route"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("Swagger UI is reachable at /swagger-ui.html")
    void shouldServeSwaggerUi() throws Exception {
        String target = mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        mockMvc.perform(get(target))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
    }
}
