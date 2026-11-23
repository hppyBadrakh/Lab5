package com.ecommerce.order.presentation;

import com.ecommerce.order.application.OrderApplicationService;
import com.ecommerce.order.application.OrderApplicationService.OrderItemCommand;
import com.ecommerce.order.domain.Order;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderApplicationService orderService;

    public OrderController(OrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        List<OrderItemCommand> commands = request.items().stream()
                .map(item -> new OrderItemCommand(item.productId(), item.productName(), item.unitPrice(),
                        item.currency(), item.quantity()))
                .toList();
        Order order = orderService.createOrder(request.customerId(), commands);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.getId())
                .toUri();
        return ResponseEntity.created(location).body(OrderResponse.fromDomain(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable String id) {
        return ResponseEntity.ok(OrderResponse.fromDomain(orderService.getOrderById(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String id) {
        return ResponseEntity.ok(OrderResponse.fromDomain(orderService.cancelOrder(id)));
    }
}
