package mn.edu.num.sad.ecommerce.catalogue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CachingCatalogueProxy implements Catalogue {

    private final Catalogue delegate;
    private final Map<String, Optional<CatalogueItem>> cache =
            new HashMap<>();

    public CachingCatalogueProxy(Catalogue delegate) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "delegate must not be null"
        );
    }

    @Override
    public synchronized Optional<CatalogueItem> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");

        if (cache.containsKey(id)) {
            return cache.get(id);
        }

        Optional<CatalogueItem> result = Objects.requireNonNull(
                delegate.findById(id),
                "delegate must return a non-null Optional"
        );

        cache.put(id, result);
        return result;
    }
}