package com.ecommerce.order.infrastructure.adapter.in.web;

import com.ecommerce.order.domain.port.in.ManageOrdersUseCase;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.AddressCommand;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.OrderLineCommand;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.PlaceOrderCommand;
import com.ecommerce.order.domain.model.Order;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final ManageOrdersUseCase orderService;

    public OrderController(ManageOrdersUseCase orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.placeOrder(toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.getId().value())
                .toUri();
        return ResponseEntity.created(location).body(OrderResponse.fromDomain(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String id) {
        return ResponseEntity.ok(OrderResponse.fromDomain(orderService.getOrder(id)));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<OrderResponse> payOrder(@PathVariable String id) {
        return ResponseEntity.ok(OrderResponse.fromDomain(orderService.payOrder(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String id) {
        return ResponseEntity.ok(OrderResponse.fromDomain(orderService.cancelOrder(id)));
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
