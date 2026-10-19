package mn.edu.num.sad.ecommerce.checkout;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutFacadeTest {

    @Test
    void shouldProducePopulatedReceiptOnHappyPath() {
        InventoryService inventory = new InventoryService() {
            @Override
            public boolean isAvailable(String sku, int quantity) {
                return true;
            }

            @Override
            public void reserve(String sku, int quantity) {
            }

            @Override
            public void release(String sku, int quantity) {
            }
        };

        PaymentGateway payment =
                (customerId, amount, token) -> "TX-OK";

        OrderService orders =
                (customerId, sku, quantity, amount) -> "ORDER-1";

        CheckoutFacade facade =
                new CheckoutFacade(inventory, payment, orders);

        Receipt receipt = facade.checkout(
                new CheckoutRequest(
                        "C-1",
                        "SKU-A",
                        1,
                        new BigDecimal("100.00"),
                        "tok_123"
                )
        );

        assertNotNull(receipt.orderId());
        assertEquals("ORDER-1", receipt.orderId());
        assertEquals("TX-OK", receipt.transactionReference());
        assertEquals("C-1", receipt.customerId());
        assertEquals(new BigDecimal("100.00"), receipt.totalAmount());
        assertNotNull(receipt.createdAt());
    }

    @Test
    void shouldHaltPaymentWhenStockIsInsufficient() {
        AtomicBoolean paymentCalled = new AtomicBoolean(false);

        InventoryService inventory = new InventoryService() {
            @Override
            public boolean isAvailable(String sku, int quantity) {
                return false;
            }

            @Override
            public void reserve(String sku, int quantity) {
                fail("Inventory must not be reserved");
            }

            @Override
            public void release(String sku, int quantity) {
            }
        };

        PaymentGateway payment = (customerId, amount, token) -> {
            paymentCalled.set(true);
            return "TX-OK";
        };

        OrderService orders =
                (customerId, sku, quantity, amount) -> "ORDER-1";

        CheckoutFacade facade =
                new CheckoutFacade(inventory, payment, orders);

        CheckoutRequest request = new CheckoutRequest(
                "C-1",
                "SKU-A",
                1,
                new BigDecimal("100.00"),
                "tok_123"
        );

        assertThrows(
                InsufficientStockException.class,
                () -> facade.checkout(request)
        );

        assertFalse(
                paymentCalled.get(),
                "Payment must not execute when stock is insufficient"
        );
    }
}