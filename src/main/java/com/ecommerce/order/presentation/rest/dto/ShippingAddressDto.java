package com.ecommerce.order.presentation.rest.dto;

import com.ecommerce.order.domain.model.ShippingAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(name = "ShippingAddress", description = "Where the order is delivered")
public record ShippingAddressDto(
        @Schema(example = "Peace Ave 1")
        @NotBlank String street,

        @Schema(example = "Ulaanbaatar")
        @NotBlank String city,

        @Schema(example = "14200")
        @NotBlank String postalCode,

        @Schema(description = "ISO 3166-1 alpha-2 country code", example = "MN")
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO country code") String countryCode
) {

    static ShippingAddressDto fromDomain(ShippingAddress address) {
        return new ShippingAddressDto(address.street(), address.city(), address.postalCode(), address.countryCode());
    }
}
