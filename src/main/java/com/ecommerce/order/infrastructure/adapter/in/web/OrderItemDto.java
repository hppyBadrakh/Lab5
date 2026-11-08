package com.ecommerce.order.infrastructure.adapter.in.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderItemDto(
        @NotBlank @Pattern(regexp = "[A-Z0-9]{3,12}-[A-Z0-9]{2,6}", message = "must look like ELEC-1001") String sku,
        @NotNull @Positive @Digits(integer = 7, fraction = 2) BigDecimal unitPrice,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase currency code") String currency,
        @Positive @Max(999) int quantity
) {
}
