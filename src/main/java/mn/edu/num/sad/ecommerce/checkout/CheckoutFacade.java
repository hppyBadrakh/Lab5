package mn.edu.num.sad.ecommerce.checkout;

import java.time.Instant;
import java.util.Objects;

public final class CheckoutFacade {

    private final InventoryService inventory;
    private final PaymentGateway payment;
    private final OrderService orders;

    public CheckoutFacade(
            InventoryService inventory,
            PaymentGateway payment,
            OrderService orders
    ) {
        this.inventory = Objects.requireNonNull(inventory);
        this.payment = Objects.requireNonNull(payment);
        this.orders = Objects.requireNonNull(orders);
    }

    public Receipt checkout(CheckoutRequest request) {
        Objects.requireNonNull(request, "request");

        if (!inventory.isAvailable(
                request.sku(), request.quantity())) {
            throw new InsufficientStockException(
                    request.sku(), request.quantity());
        }

        inventory.reserve(request.sku(), request.quantity());

        String transactionReference;

        try {
            transactionReference = payment.charge(
                    request.customerId(),
                    request.totalAmount(),
                    request.paymentToken()
            );
        } catch (RuntimeException exception) {
            inventory.release(request.sku(), request.quantity());
            throw exception;
        }

        String orderId = orders.createOrder(
                request.customerId(),
                request.sku(),
                request.quantity(),
                request.totalAmount()
        );

        return new Receipt(
                orderId,
                request.customerId(),
                request.totalAmount(),
                transactionReference,
                Instant.now()
        );
    }
}