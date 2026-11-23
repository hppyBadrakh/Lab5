package com.ecommerce.order.presentation.rest.dto;

import com.ecommerce.order.domain.model.OrderStatus;
import com.ecommerce.order.domain.port.in.ManageOrdersUseCase.PaymentReceipt;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The payment gateway is unavailable. The payment intent was accepted and will be settled later.")
public record DeferredPaymentResponse(
        @Schema(example = "0b9f6c1e-6f0a-4c58-9a59-3f1f3f1c2b7d")
        String orderId,

        @Schema(example = "PAYMENT_PENDING")
        OrderStatus status,

        @Schema(description = "Token of the queued payment intent", example = "DEFERRED-5d0c7c8e-2f6b-4c3a-9d1e-8a7b6c5d4e3f")
        String paymentReference,

        @Schema(example = "Payment gateway temporarily unavailable. Payment queued for background settlement.")
        String message
) {

    public static DeferredPaymentResponse fromDomain(PaymentReceipt receipt) {
        return new DeferredPaymentResponse(receipt.order().getId().value(), receipt.order().getStatus(),
                receipt.payment().reference(), receipt.payment().message());
    }
}
