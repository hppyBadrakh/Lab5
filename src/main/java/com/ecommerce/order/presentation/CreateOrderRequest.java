package com.ecommerce.order.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 64) String customerId,
        @NotNull @Valid ShippingAddressDto shippingAddress,
        @NotEmpty List<@Valid OrderItemDto> items
) {
}
