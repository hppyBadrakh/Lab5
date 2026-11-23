package com.ecommerce.order.presentation.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "One line of a new order")
public record OrderItemRequest(
        @Schema(description = "Stock Keeping Unit in the form CATEGORY-CODE", example = "ELEC-1001")
        @NotBlank @Pattern(regexp = "[A-Z0-9]{3,12}-[A-Z0-9]{2,6}", message = "must look like ELEC-1001") String sku,

        @Schema(description = "Price of one unit, with at most 2 decimals", example = "25.50")
        @NotNull @Positive @Digits(integer = 7, fraction = 2) BigDecimal unitPrice,

        @Schema(description = "Three-letter ISO 4217 currency code", example = "USD")
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase currency code") String currency,

        @Schema(description = "Number of units ordered", example = "2", minimum = "1", maximum = "999")
        @Positive @Max(999) int quantity
) {
}
