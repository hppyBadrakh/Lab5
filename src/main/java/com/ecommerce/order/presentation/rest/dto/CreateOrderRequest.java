package com.ecommerce.order.presentation.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Payload required to place a new customer order. The server assigns the order id and status.")
public record CreateOrderRequest(
        @Schema(description = "Id of the customer placing the order. The customer itself is owned by the Customer Service.",
                example = "CUST-1", maxLength = 64)
        @NotBlank @Size(max = 64) String customerId,

        @Schema(description = "Delivery address, copied into the order as a snapshot")
        @NotNull @Valid ShippingAddressDto shippingAddress,

        @Schema(description = "Line items. At least one is required, and all must use the same currency.")
        @NotEmpty List<@Valid OrderItemRequest> items
) {
}
