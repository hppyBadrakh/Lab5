# M2 Architecture Vision

Closed with Week 10. The frozen baseline and the list of what is locked are in [m2-milestone-close.md](m2-milestone-close.md).

## C4 Container Diagram

The container view at M2 close is in [m2-milestone-close.md](m2-milestone-close.md#2-c4-container-view-at-m2-close). The target with all five services is described in [service-decomposition.md](service-decomposition.md).

## Subsystem-to-Pattern Mapping

The Week 5 structural patterns live in the monolith package `mn.edu.num.sad.ecommerce`. The last column says where each one ends up after the decomposition.

| Subsystem | Pattern | Architectural responsibility | Target service |
|---|---|---|---|
| Customer integration | Adapter (`CustomerStoreAdapter`) | Makes the legacy customer store usable through the `CustomerStore` interface the rest of the code expects | Customer Service |
| Checkout flow | Facade (`CheckoutFacade`) | One entry point that runs inventory, payment and order creation in sequence. Its synchronous chain is what the decomposition replaces with `OrderPlaced` events | Dissolved: Order Service plus events |
| Product repository | Decorator (`LoggingProductRepository`) | Adds logging around a `ProductRepository` without changing it | Catalogue Service |
| Order line items | Composite (`ItemBundle`, `SingleItem`) | Treats a single item and a bundle of items uniformly | Catalogue Service (bundles are a catalogue concept; an order stores flat lines per SKU) |
| Product catalogue | Proxy (`CachingCatalogueProxy`) | Caches catalogue lookups in front of the real `Catalogue` | Catalogue Service |
