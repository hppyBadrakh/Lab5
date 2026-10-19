package mn.edu.num.sad.ecommerce.catalogue;

import java.util.Optional;

public interface Catalogue {

    Optional<CatalogueItem> findById(String id);
}