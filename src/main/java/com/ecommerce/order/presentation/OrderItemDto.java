package com.ecommerce.order.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record OrderItemDto(
        @NotBlank String productId,
        @NotBlank String productName,
        @Positive double unitPrice,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase currency code") String currency,
        @Positive int quantity
) {
}
