package com.ecommerce.order.presentation.rest;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.AddressCommand;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.OrderLineCommand;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.PaymentReceipt;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.PlaceOrderCommand;
import com.ecommerce.order.presentation.rest.dto.CreateOrderRequest;
import com.ecommerce.order.presentation.rest.dto.CustomerOrderSummaryResponse;
import com.ecommerce.order.presentation.rest.dto.DeferredPaymentResponse;
import com.ecommerce.order.presentation.rest.dto.OrderResponse;
import com.ecommerce.order.presentation.rest.dto.ShippingAddressDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Driving REST adapter of the Order Service. URIs name resources (nouns), HTTP methods are the verbs.
 * State changes are modelled as sub-resources that are put in place: {@code PUT .../payment} and
 * {@code PUT .../cancellation}. Depends on the inbound port only, never on the service class.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Orders", description = "Place, read, pay and cancel customer orders")
public class OrderRestController {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ManageOrdersUseCase orderService;

    public OrderRestController(ManageOrdersUseCase orderService) {
        this.orderService = orderService;
    }

    @PostMapping(value = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Place a new order",
            description = "Creates the order in status PLACED. Audit and customer notification run afterwards "
                    + "on an asynchronous side-channel and never delay or fail this response.")
    @ApiResponse(responseCode = "201", description = "Order placed",
            headers = @Header(name = "Location", description = "URI of the new order",
                    schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "400", description = "Validation failed, or the order breaks a business rule "
            + "(mixed currencies, total above 10,000)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Checkout capacity exceeded (rate limiter). Retry after the "
            + "number of seconds in the Retry-After header",
            headers = @Header(name = "Retry-After", description = "Seconds to wait", schema = @Schema(type = "integer")),
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.placeOrder(toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(order.getId().value())
                .toUri();
        return ResponseEntity.created(location).body(OrderResponse.fromDomain(order));
    }

    @GetMapping(value = "/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Retrieve an order by its id")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "No order has this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Order id returned when the order was placed",
                    example = "0b9f6c1e-6f0a-4c58-9a59-3f1f3f1c2b7d")
            @PathVariable String orderId) {
        return ResponseEntity.ok(OrderResponse.fromDomain(orderService.getOrder(orderId)));
    }

    @GetMapping(value = "/customers/{customerId}/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the orders of a customer, newest first",
            description = "Returns an empty list for a customer without orders. The Order Service does not own "
                    + "customers, so it cannot tell an unknown customer from one who never ordered.")
    @ApiResponse(responseCode = "200", description = "Order summaries, possibly empty")
    @ApiResponse(responseCode = "400", description = "Customer id is longer than 64 characters",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<CustomerOrderSummaryResponse>> listOrdersByCustomer(
            @Parameter(description = "Customer id as used when placing orders", example = "CUST-1")
            @PathVariable String customerId) {
        return ResponseEntity.ok(orderService.listOrdersForCustomer(customerId).stream()
                .map(CustomerOrderSummaryResponse::fromDomain)
                .toList());
    }

    @PutMapping("/orders/{orderId}/payment")
    @Operation(summary = "Pay a placed order through the payment gateway",
            description = "Charges the order total. If the payment gateway is slow or down, the circuit breaker "
                    + "answers at once: the payment intent is queued and the order becomes PAYMENT_PENDING (202). "
                    + "Calling this again for a PAYMENT_PENDING order retries the charge.")
    @ApiResponse(responseCode = "204", description = "Gateway charged the order. Order is now PAID", content = @Content)
    @ApiResponse(responseCode = "202", description = "Gateway unavailable. Payment deferred, order is PAYMENT_PENDING",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DeferredPaymentResponse.class)))
    @ApiResponse(responseCode = "402", description = "The gateway declined the charge. The order stays payable",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No order has this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The order is not in a state that can be paid",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<DeferredPaymentResponse> payOrder(
            @Parameter(description = "Order id", example = "0b9f6c1e-6f0a-4c58-9a59-3f1f3f1c2b7d")
            @PathVariable String orderId) {
        PaymentReceipt receipt = orderService.payOrder(orderId);
        if (receipt.deferred()) {
            return ResponseEntity.accepted().body(DeferredPaymentResponse.fromDomain(receipt));
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/orders/{orderId}/cancellation")
    @Operation(summary = "Cancel an order")
    @ApiResponse(responseCode = "204", description = "Order is now CANCELLED", content = @Content)
    @ApiResponse(responseCode = "404", description = "No order has this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The order was already cancelled",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> cancelOrder(
            @Parameter(description = "Order id", example = "0b9f6c1e-6f0a-4c58-9a59-3f1f3f1c2b7d")
            @PathVariable String orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    private static PlaceOrderCommand toCommand(CreateOrderRequest request) {
        ShippingAddressDto address = request.shippingAddress();
        return new PlaceOrderCommand(
                request.customerId(),
                new AddressCommand(address.street(), address.city(), address.postalCode(), address.countryCode()),
                request.items().stream()
                        .map(item -> new OrderLineCommand(item.sku(), item.unitPrice(), item.currency(), item.quantity()))
                        .toList());
    }
}
