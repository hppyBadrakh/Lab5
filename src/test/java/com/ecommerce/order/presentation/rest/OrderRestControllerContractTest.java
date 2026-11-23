package com.ecommerce.order.presentation.rest;

import com.ecommerce.order.application.InvalidOrderOperationException;
import com.ecommerce.order.application.InvalidOrderRequestException;
import com.ecommerce.order.application.OrderNotFoundException;
import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static com.ecommerce.order.OrderFixtures.order;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract of the REST adapter in isolation: status codes, headers and body shapes
 * for 201, 200, 204, 400, 404, 405, 409 and 500. The use case is mocked.
 */
@WebMvcTest(OrderRestController.class)
class OrderRestControllerContractTest {

    private static final String ADDRESS = """
            "shippingAddress":{"street":"Peace Ave 1","city":"Ulaanbaatar","postalCode":"14200","countryCode":"MN"}""";
    private static final String VALID_BODY = """
            {"customerId":"CUST-1",%s,"items":[
              {"sku":"ELEC-1001","unitPrice":100.00,"currency":"USD","quantity":2}
            ]}
            """.formatted(ADDRESS);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageOrdersUseCase orderService;

    @Test
    @DisplayName("POST /api/v1/orders - 201 Created with Location header and a PLACED order")
    void shouldReturn201WhenPlaced() throws Exception {
        given(orderService.placeOrder(any())).willReturn(order("ORD-1", OrderStatus.PLACED));

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/ORD-1")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.total").value(200.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.shippingAddress.countryCode").value("MN"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 200 OK with order body")
    void shouldReturn200WhenFound() throws Exception {
        given(orderService.getOrder("ORD-1")).willReturn(order("ORD-1", OrderStatus.PAID));

        mockMvc.perform(get("/api/v1/orders/ORD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.items[0].sku").value("ELEC-1001"))
                .andExpect(jsonPath("$.items[0].lineId").exists())
                .andExpect(jsonPath("$.items[0].subtotal").value(200.00));
    }

    @Test
    @DisplayName("GET /api/v1/customers/{customerId}/orders - 200 OK with order summaries")
    void shouldReturn200WithCustomerOrders() throws Exception {
        given(orderService.listOrdersForCustomer("CUST-1")).willReturn(List.of(
                order("ORD-2", "CUST-1", "10.00", "USD", 3, OrderStatus.PAID, Instant.parse("2026-11-02T00:00:00Z")),
                order("ORD-1", OrderStatus.PLACED)));

        mockMvc.perform(get("/api/v1/customers/CUST-1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderId").value("ORD-2"))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].total").value(30.00))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].itemCount").value(1))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-02T00:00:00Z"))
                .andExpect(jsonPath("$[0].items").doesNotExist())
                .andExpect(jsonPath("$[1].orderId").value("ORD-1"));
    }

    @Test
    @DisplayName("GET /api/v1/customers/{customerId}/orders - 200 OK with an empty list for a customer without orders")
    void shouldReturn200WithEmptyListForCustomerWithoutOrders() throws Exception {
        given(orderService.listOrdersForCustomer("CUST-NEW")).willReturn(List.of());

        mockMvc.perform(get("/api/v1/customers/CUST-NEW/orders"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{orderId}/payment - 204 No Content with an empty body")
    void shouldReturn204WhenPaid() throws Exception {
        given(orderService.payOrder("ORD-1")).willReturn(order("ORD-1", OrderStatus.PAID));

        mockMvc.perform(put("/api/v1/orders/ORD-1/payment"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(orderService).payOrder("ORD-1");
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{orderId}/cancellation - 204 No Content with an empty body")
    void shouldReturn204WhenCancelled() throws Exception {
        given(orderService.cancelOrder("ORD-1")).willReturn(order("ORD-1", OrderStatus.CANCELLED));

        mockMvc.perform(put("/api/v1/orders/ORD-1/cancellation"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(orderService).cancelOrder("ORD-1");
    }

    @Test
    @DisplayName("POST /api/v1/orders - 400 ProblemDetail listing invalid fields")
    void shouldReturn400WhenValidationFails() throws Exception {
        String body = """
                {"customerId":" ",
                 "shippingAddress":{"street":"","city":"Ulaanbaatar","postalCode":"14200","countryCode":"mn"},
                 "items":[{"sku":"p1","unitPrice":-1,"currency":"usd","quantity":1000}]}
                """;

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.invalidFields.customerId").exists())
                .andExpect(jsonPath("$.invalidFields['shippingAddress.street']").exists())
                .andExpect(jsonPath("$.invalidFields['shippingAddress.countryCode']").exists())
                .andExpect(jsonPath("$.invalidFields['items[0].sku']").exists())
                .andExpect(jsonPath("$.invalidFields['items[0].unitPrice']").exists())
                .andExpect(jsonPath("$.invalidFields['items[0].currency']").exists())
                .andExpect(jsonPath("$.invalidFields['items[0].quantity']").exists());

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST /api/v1/orders - 400 for an empty item list or a missing address")
    void shouldReturn400WhenItemsOrAddressMissing() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"CUST-1\"," + ADDRESS + ",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields.items").exists());
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace(ADDRESS + ",", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields.shippingAddress").exists());
    }

    @Test
    @DisplayName("POST /api/v1/orders - 400 for a price with more than 2 decimals")
    void shouldReturn400ForSubCentPrice() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("100.00", "100.005")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields['items[0].unitPrice']").exists());
    }

    @Test
    @DisplayName("POST /api/v1/orders - 400 ProblemDetail for malformed JSON, not 500")
    void shouldReturn400WhenJsonMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("POST /api/v1/orders - 400 ProblemDetail when the aggregate rejects the request")
    void shouldReturn400WhenDomainRejects() throws Exception {
        given(orderService.placeOrder(any()))
                .willThrow(new InvalidOrderRequestException("Order total 12000.00 USD would exceed the limit of 10000.00 USD"));

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Business Rule Violation"))
                .andExpect(jsonPath("$.detail").value(containsString("would exceed the limit")));
    }

    @Test
    @DisplayName("POST /api/v1/orders - 415 ProblemDetail for a non-JSON body")
    void shouldReturn415ForNonJsonBody() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 404 ProblemDetail")
    void shouldReturn404WhenNotFound() throws Exception {
        given(orderService.getOrder("UNKNOWN")).willThrow(new OrderNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/v1/orders/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Order Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Order not found: UNKNOWN"));
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{orderId}/cancellation - 404 ProblemDetail for an unknown order")
    void shouldReturn404WhenCancellingUnknownOrder() throws Exception {
        given(orderService.cancelOrder("UNKNOWN")).willThrow(new OrderNotFoundException("UNKNOWN"));

        mockMvc.perform(put("/api/v1/orders/UNKNOWN/cancellation"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order Not Found"));
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{orderId} - 405 ProblemDetail, not 500")
    void shouldReturn405ForUnsupportedMethod() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/ORD-1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{orderId}/cancellation - 409 ProblemDetail when already cancelled")
    void shouldReturn409WhenAlreadyCancelled() throws Exception {
        given(orderService.cancelOrder("ORD-CANCELLED"))
                .willThrow(new InvalidOrderOperationException("Illegal state transition from CANCELLED to CANCELLED"));

        mockMvc.perform(put("/api/v1/orders/ORD-CANCELLED/cancellation"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("State Transition Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Illegal state transition from CANCELLED to CANCELLED"));
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{orderId}/payment - 409 ProblemDetail on illegal transition")
    void shouldReturn409WhenPayingCancelledOrder() throws Exception {
        given(orderService.payOrder("ORD-CANCELLED"))
                .willThrow(new InvalidOrderOperationException("Illegal state transition from CANCELLED to PAID"));

        mockMvc.perform(put("/api/v1/orders/ORD-CANCELLED/payment"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("State Transition Conflict"))
                .andExpect(jsonPath("$.detail").value("Illegal state transition from CANCELLED to PAID"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId} - 500 ProblemDetail that leaks no internals")
    void shouldReturn500WithoutLeakingDetails() throws Exception {
        given(orderService.getOrder("BOOM"))
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
