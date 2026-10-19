package mn.edu.num.sad.ecommerce.product;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

public final class LoggingProductRepository implements ProductRepository {

    private final ProductRepository delegate;

    private final LongAdder findByIdCalls = new LongAdder();
    private final LongAdder findAllCalls = new LongAdder();

    public LoggingProductRepository(ProductRepository delegate) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "delegate must not be null"
        );
    }

    @Override
    public Optional<Product> findById(String id) {
        findByIdCalls.increment();
        return delegate.findById(id);
    }

    @Override
    public List<Product> findAll() {
        findAllCalls.increment();
        return delegate.findAll();
    }

    public long getFindByIdCallCount() {
        return findByIdCalls.sum();
    }

    public long getFindAllCallCount() {
        return findAllCalls.sum();
    }
}