package com.ecommerce.order.presentation.rest.dto;

import com.ecommerce.order.domain.model.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "One line of an order")
public record OrderItemResponse(
        @Schema(description = "Identity of this line inside its order")
        UUID lineId,

        @Schema(example = "ELEC-1001")
        String sku,

        @Schema(description = "Price of one unit, as agreed at checkout", example = "25.50")
        BigDecimal unitPrice,

        @Schema(example = "2")
        int quantity,

        @Schema(description = "unitPrice x quantity", example = "51.00")
        BigDecimal subtotal
) {

    static OrderItemResponse fromDomain(OrderItem item) {
        return new OrderItemResponse(item.getLineId(), item.getSku().value(), item.getUnitPrice().amount(),
                item.getQuantity().value(), item.calculateSubtotal().amount());
    }
}
