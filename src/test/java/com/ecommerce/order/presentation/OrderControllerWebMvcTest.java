package com.ecommerce.order.presentation;

import com.ecommerce.order.application.InvalidOrderOperationException;
import com.ecommerce.order.application.InvalidOrderRequestException;
import com.ecommerce.order.application.OrderApplicationService;
import com.ecommerce.order.application.OrderNotFoundException;
import com.ecommerce.order.domain.Money;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerWebMvcTest {

    private static final String VALID_BODY = """
            {"customerId":"CUST-1","items":[
              {"productId":"P1","productName":"Item","unitPrice":100.0,"currency":"USD","quantity":2}
            ]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderApplicationService orderService;

    private static Order order(String id, OrderStatus status) {
        List<OrderItem> items = List.of(new OrderItem("P1", "Item", Money.of(100.0, "USD"), 2));
        return new Order(id, "CUST-1", items, status, Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    @DisplayName("POST /api/v1/orders - 201 Created with Location header")
    void shouldReturn201WhenCreated() throws Exception {
        given(orderService.createOrder(anyString(), any())).willReturn(order("ORD-1", OrderStatus.PENDING));

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/ORD-1")))
                .andExpect(jsonPath("$.orderId").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(200.00))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} - 200 OK with order body")
    void shouldReturn200WhenFound() throws Exception {
        given(orderService.getOrderById("ORD-1")).willReturn(order("ORD-1", OrderStatus.PAID));

        mockMvc.perform(get("/api/v1/orders/ORD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.items[0].productId").value("P1"))
                .andExpect(jsonPath("$.items[0].subtotal").value(200.00));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel - 200 OK")
    void shouldReturn200WhenCancelled() throws Exception {
        given(orderService.cancelOrder("ORD-1")).willReturn(order("ORD-1", OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/orders/ORD-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/orders - 400 ProblemDetail listing invalid fields")
    void shouldReturn400WhenValidationFails() throws Exception {
        String body = """
                {"customerId":" ","items":[
                  {"productId":"P1","productName":"Item","unitPrice":-1,"currency":"usd","quantity":0}
                ]}
                """;

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.invalidFields.customerId").exists())
                .andExpect(jsonPath("$.invalidFields['items[0].unitPrice']").exists())
                .andExpect(jsonPath("$.invalidFields['items[0].currency']").exists())
                .andExpect(jsonPath("$.invalidFields['items[0].quantity']").exists());

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST /api/v1/orders - 400 ProblemDetail for an empty item list")
    void shouldReturn400WhenItemsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"CUST-1\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields.items").exists());
    }

    @Test
    @DisplayName("POST /api/v1/orders - 400 ProblemDetail for malformed JSON, not 500")
    void shouldReturn400WhenJsonMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("POST /api/v1/orders - 400 ProblemDetail when the domain rejects the request")
    void shouldReturn400WhenDomainRejects() throws Exception {
        given(orderService.createOrder(anyString(), any()))
                .willThrow(new InvalidOrderRequestException("All items must use the same currency"));

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("All items must use the same currency"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} - 404 ProblemDetail")
    void shouldReturn404WhenNotFound() throws Exception {
        given(orderService.getOrderById("UNKNOWN")).willThrow(new OrderNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/v1/orders/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Order not found: UNKNOWN"));
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - 405 ProblemDetail, not 500")
    void shouldReturn405ForUnsupportedMethod() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/ORD-1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel - 409 ProblemDetail on illegal transition")
    void shouldReturn409OnConflict() throws Exception {
        given(orderService.cancelOrder("ORD-DELIVERED"))
                .willThrow(new InvalidOrderOperationException("Illegal state transition from DELIVERED to CANCELLED"));

        mockMvc.perform(post("/api/v1/orders/ORD-DELIVERED/cancel"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Illegal state transition from DELIVERED to CANCELLED"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} - 500 ProblemDetail that leaks no internals")
    void shouldReturn500WithoutLeakingDetails() throws Exception {
        given(orderService.getOrderById("BOOM"))
                .willThrow(new IllegalStateException("jdbc:h2:mem:test password=secret"));

        mockMvc.perform(get("/api/v1/orders/BOOM"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
