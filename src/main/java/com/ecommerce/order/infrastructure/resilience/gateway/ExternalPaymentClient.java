package com.ecommerce.order.infrastructure.resilience.gateway;

import com.ecommerce.order.domain.model.Money;
import com.ecommerce.order.domain.model.OrderId;
import com.ecommerce.order.domain.model.PaymentResult;

/**
 * Blocking client for the third-party payment gateway (Payment context, Open Host Service).
 * Every call occupies the calling thread until the gateway answers or the read timeout expires.
 */
public interface ExternalPaymentClient {

    /**
     * @throws PaymentGatewayException when the gateway does not answer within the read timeout or is unreachable
     */
    PaymentResult callRemoteGateway(OrderId orderId, Money amount);
}
