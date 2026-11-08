package com.ecommerce.order.infrastructure.adapter.in.web;

import com.ecommerce.order.domain.model.ShippingAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ShippingAddressDto(
        @NotBlank String street,
        @NotBlank String city,
        @NotBlank String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO country code") String countryCode
) {

    static ShippingAddressDto fromDomain(ShippingAddress address) {
        return new ShippingAddressDto(address.street(), address.city(), address.postalCode(), address.countryCode());
    }
}
