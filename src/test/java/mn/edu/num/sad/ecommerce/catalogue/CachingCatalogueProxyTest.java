package mn.edu.num.sad.ecommerce.catalogue;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CachingCatalogueProxyTest {

    @Test
    void repeatedLookupCallsDelegateOnlyOnce() {
        CatalogueItem item = new CatalogueItem(
                "P-1",
                "Laptop",
                new BigDecimal("1200.00")
        );

        AtomicInteger calls = new AtomicInteger();

        Catalogue realCatalogue = id -> {
            calls.incrementAndGet();
            return Optional.of(item);
        };

        Catalogue proxy = new CachingCatalogueProxy(realCatalogue);

        assertEquals(Optional.of(item), proxy.findById("P-1"));
        assertEquals(Optional.of(item), proxy.findById("P-1"));

        assertEquals(1, calls.get());
    }

    @Test
    void returnsTheSameCachedObjectOnRepeatedLookup() {
        CatalogueItem item = new CatalogueItem(
                "P-2",
                "Monitor",
                new BigDecimal("250.00")
        );

        Catalogue realCatalogue = id -> Optional.of(item);
        Catalogue proxy = new CachingCatalogueProxy(realCatalogue);

        Optional<CatalogueItem> first = proxy.findById("P-2");
        Optional<CatalogueItem> second = proxy.findById("P-2");

        assertSame(first, second);
        assertSame(item, first.orElseThrow());
    }

    @Test
    void cachesMissingItemsToo() {
        AtomicInteger calls = new AtomicInteger();

        Catalogue realCatalogue = id -> {
            calls.incrementAndGet();
            return Optional.empty();
        };

        Catalogue proxy = new CachingCatalogueProxy(realCatalogue);

        assertTrue(proxy.findById("MISSING").isEmpty());
        assertTrue(proxy.findById("MISSING").isEmpty());

        assertEquals(1, calls.get());
    }
}