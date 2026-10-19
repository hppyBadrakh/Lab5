package mn.edu.num.sad.ecommerce.checkout;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String sku, int quantity) {
        super("Insufficient stock for SKU " + sku
                + "; requested quantity: " + quantity);
    }
}