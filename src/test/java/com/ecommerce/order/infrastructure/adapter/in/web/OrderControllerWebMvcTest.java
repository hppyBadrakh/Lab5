package com.ecommerce.order.infrastructure.adapter.in.web;

import com.ecommerce.order.application.InvalidOrderOperationException;
import com.ecommerce.order.application.InvalidOrderRequestException;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase;
import com.ecommerce.order.application.OrderNotFoundException;
import com.ecommerce.order.domain.model.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.ecommerce.order.OrderFixtures.order;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
                .andExpect(jsonPath("$.orderId").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.total").value(200.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.shippingAddress.countryCode").value("MN"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} - 200 OK with order body")
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
    @DisplayName("POST /api/v1/orders/{id}/pay - 200 OK")
    void shouldReturn200WhenPaid() throws Exception {
        given(orderService.payOrder("ORD-1")).willReturn(order("ORD-1", OrderStatus.PAID));

        mockMvc.perform(post("/api/v1/orders/ORD-1/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
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
                {"customerId":" ",
                 "shippingAddress":{"street":"","city":"Ulaanbaatar","postalCode":"14200","countryCode":"mn"},
                 "items":[{"sku":"p1","unitPrice":-1,"currency":"usd","quantity":1000}]}
                """;

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
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
                .andExpect(jsonPath("$.detail").value(containsString("would exceed the limit")));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} - 404 ProblemDetail")
    void shouldReturn404WhenNotFound() throws Exception {
        given(orderService.getOrder("UNKNOWN")).willThrow(new OrderNotFoundException("UNKNOWN"));

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
    @DisplayName("POST /api/v1/orders/{id}/pay - 409 ProblemDetail on illegal transition")
    void shouldReturn409OnConflict() throws Exception {
        given(orderService.payOrder("ORD-CANCELLED"))
                .willThrow(new InvalidOrderOperationException("Illegal state transition from CANCELLED to PAID"));

        mockMvc.perform(post("/api/v1/orders/ORD-CANCELLED/pay"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Illegal state transition from CANCELLED to PAID"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} - 500 ProblemDetail that leaks no internals")
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
