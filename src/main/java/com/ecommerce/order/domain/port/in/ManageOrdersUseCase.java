package com.ecommerce.order.domain.port.in;

import com.ecommerce.order.domain.model.Order;
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

    Order payOrder(String orderId);

    Order cancelOrder(String orderId);

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
