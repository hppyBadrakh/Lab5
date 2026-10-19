package mn.edu.num.sad.ecommerce.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LoggingProductRepositoryTest {

    @Test
    void delegatesCallsAndPreservesRepositoryResults() {
        Product product = new Product(
                "P-1",
                "Keyboard",
                new BigDecimal("49.99")
        );

        List<Product> products = List.of(product);

        ProductRepository repository = new ProductRepository() {
            @Override
            public Optional<Product> findById(String id) {
                return "P-1".equals(id)
                        ? Optional.of(product)
                        : Optional.empty();
            }

            @Override
            public List<Product> findAll() {
                return products;
            }
        };

        LoggingProductRepository decorator =
                new LoggingProductRepository(repository);

        assertEquals(Optional.of(product), decorator.findById("P-1"));
        assertEquals(Optional.empty(), decorator.findById("UNKNOWN"));
        assertSame(products, decorator.findAll());

        assertEquals(2, decorator.getFindByIdCallCount());
        assertEquals(1, decorator.getFindAllCallCount());
    }

    @Test
    void recordsCallsWithoutChangingMissingProductBehavior() {
        ProductRepository repository = new ProductRepository() {
            @Override
            public Optional<Product> findById(String id) {
                return Optional.empty();
            }

            @Override
            public List<Product> findAll() {
                return List.of();
            }
        };

        LoggingProductRepository decorator =
                new LoggingProductRepository(repository);

        assertEquals(0, decorator.getFindByIdCallCount());
        assertEquals(0, decorator.getFindAllCallCount());

        assertTrue(decorator.findById("MISSING").isEmpty());
        assertEquals(1, decorator.getFindByIdCallCount());

        assertTrue(decorator.findAll().isEmpty());
        assertEquals(1, decorator.getFindAllCallCount());
    }
}