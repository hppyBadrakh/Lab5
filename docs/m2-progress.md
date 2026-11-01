# M2 Progress Note

During this milestone, I moved order handling from the single legacy endpoint into a layered Spring Boot 3 service on Java 21, with separate presentation, application, domain, and infrastructure packages. The controller now only maps validated DTOs and returns RFC 7807 errors, the pricing and status rules live in pure Java domain classes, and storage sits behind an `OrderRepository` port with an in-memory adapter. ArchUnit rules enforce the dependency direction and the absence of package cycles in every build. The container view below reflects this decomposition; PostgreSQL and the JPA adapter are planned for M3 and do not exist yet. See [ADR-0005](adr/0005-layered-mvc-for-orders.md) for the decision and its costs.

## C4 Container View: Order Subsystem

```mermaid
flowchart TB
    customer["Online Customer<br/>[Person]"]
    admin["Store Administrator<br/>[Person]"]
    storefront["Web Storefront<br/>[Container: Single Page App]<br/>future, not part of this slice"]

    customer -->|HTTPS| storefront
    admin -->|HTTPS| storefront
    storefront -->|"REST / JSON"| presentation

    subgraph app["Order Service [Container: Spring Boot 3, Java 21]"]
        presentation["Presentation layer<br/>OrderController, DTOs,<br/>GlobalExceptionHandler (RFC 7807)"]
        application["Application layer<br/>OrderApplicationService,<br/>OrderRepository port"]
        domain["Domain layer (pure Java)<br/>Order, OrderItem,<br/>Money, OrderStatus"]
        memory["Infrastructure layer<br/>InMemoryOrderRepository<br/>(ConcurrentHashMap)"]
        jpa["JpaOrderRepository<br/>future (M3)"]
        export["Export pipeline (bonus)<br/>Pipe-and-Filter CSV"]

        presentation -->|calls| application
        presentation -.->|"maps to DTOs"| domain
        application -->|uses| domain
        memory -->|"implements port"| application
        memory -->|stores| domain
        jpa -.->|"implements port"| application
        export -->|reads| domain
    end

    postgres[("PostgreSQL<br/>future (M3)")]
    jpa -.->|SQL| postgres

    classDef future stroke-dasharray: 5 5
    class storefront,jpa,postgres future
```

Solid arrows exist in the code today. Dashed boxes and the dashed arrows leading to them are planned. The dashed arrow from presentation to domain is the one read-only dependency that exists today, for mapping responses.
