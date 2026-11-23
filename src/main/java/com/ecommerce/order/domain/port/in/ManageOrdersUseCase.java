package com.ecommerce.order.domain.port.in;

import com.ecommerce.order.domain.model.Order;
import com.ecommerce.order.domain.model.PaymentResult;
import com.ecommerce.order.domain.model.ShippingAddress;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound (driving) port: what the outside world may ask the Order context to do.
 * Driving adapters such as the REST controller depend on this interface, never on its implementation.
 */
public interface ManageOrdersUseCase {

    /** Creates a draft, adds every line, places it and stores it. Returns the PLACED order. */
    Order placeOrder(PlaceOrderCommand command);

    Order getOrder(String orderId);

    /** Every order of one customer, newest first. An unknown customer simply has no orders. */
    List<Order> listOrdersForCustomer(String customerId);

    /**
     * Charges the order through the payment port. Returns the order with its new status:
     * PAID when the gateway charged it, PAYMENT_PENDING when the gateway was unavailable and the
     * payment intent was deferred.
     */
    PaymentReceipt payOrder(String orderId);

    Order cancelOrder(String orderId);

    record PaymentReceipt(Order order, PaymentResult payment) {

        public boolean deferred() {
            return payment.status() == PaymentResult.PaymentStatus.DEFERRED_PENDING;
        }
    }

    record PlaceOrderCommand(String customerId, AddressCommand shippingAddress, List<OrderLineCommand> lines) {
    }

    record OrderLineCommand(String sku, BigDecimal unitPrice, String currency, int quantity) {
    }

    record AddressCommand(String street, String city, String postalCode, String countryCode) {

        public ShippingAddress toDomain() {
            return new ShippingAddress(street, city, postalCode, countryCode);
        }
    }
}
