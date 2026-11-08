# Bounded Contexts and Ubiquitous Language

Strategic design for the e-commerce order subsystem (Week 9, `m6-ddd`).

## Context map

```text
+-----------------------------+                    +-----------------------------+
|      INVENTORY CONTEXT      |                    |       PAYMENT CONTEXT       |
|   Supporting subdomain      |                    |     Generic subdomain       |
|   [Upstream: Supplier]      |                    |  [Upstream: Open Host]      |
+--------------+--------------+                    +--------------+--------------+
               |                                                  |
               | Customer-Supplier                                | Open Host Service +
               | (stock reservation API;                          | Published Language
               |  Order is the customer and                       | (payment provider API,
               |  negotiates the contract)                        |  Order conforms through an ACL)
               v                                                  v
+------------------------------------------------------------------------------------+
|                   ORDER PROCESSING CONTEXT  (CORE DOMAIN)                          |
|                   package: com.ecommerce.order                                     |
|                                                                                    |
|  - Order aggregate root guards line items, the order total and the lifecycle.     |
|  - Publishes domain events: OrderPlaced, OrderCancelled.                           |
|  - Domain core is pure Java 21: no Spring, Jakarta or persistence types.           |
|  - Talks to the outside only through ports (domain.port.in / domain.port.out).     |
+------------------------------------------------------------------------------------+
```

| Context | Subdomain type | Why |
|---|---|---|
| **Order Processing** | Core | Checkout rules (limits, lifecycle, currency) are what the business competes on. We build it ourselves with full DDD. |
| **Inventory** | Supporting | Needed for checkout, but stock counting is not a differentiator. Owned by another team. |
| **Payment** | Generic | Solved by off-the-shelf payment providers. We integrate, we do not build. |

### Relationships

| Upstream | Downstream | Pattern | Contract |
|---|---|---|---|
| Inventory | Order | **Customer-Supplier** | Order asks Inventory to reserve stock per SKU after an `OrderPlaced` event. Inventory plans its API around Order's needs, but Order never reads Inventory's database. |
| Payment | Order | **Open Host Service / Published Language** | Payment exposes one public API for every client. Order translates it through an anti-corruption layer (an outbound adapter), so payment terms such as `charge` or `capture` never leak into the Order model. |

**Integration rule:** the Order aggregate never calls Inventory or Payment directly. If the inventory service hangs, recording what the customer ordered must still succeed. Other contexts react to Order's domain events. Order is the source of truth for *what was ordered*. Inventory owns *what is in stock*. Payment owns *what was charged*.

## Ubiquitous Language (Order Processing context)

These terms mean exactly this inside the Order context, in code, tests, tickets and conversation. Each one has a class of the same name in `com.ecommerce.order.domain`.

| Term | Kind | Meaning |
|---|---|---|
| **Order** | Aggregate root | A commercial agreement between a customer and the merchant. It is the only entry point for changing line items, moving through the lifecycle and calculating the total. |
| **OrderItem** (line item) | Entity | One line of an order. It has its own line identity, a SKU reference, a unit price and an ordered quantity. It exists only inside its Order. |
| **OrderId** | Value object | The identity of an Order. It cannot be confused with a customer id. |
| **CustomerId** | Value object | A reference to the customer who owns the order. The Customer itself lives in another context. |
| **Sku** | Value object | Stock Keeping Unit, the product code shared with Inventory, in the format `CATEGORY-CODE` (for example `ELEC-1001`). |
| **Quantity** | Value object | How many units of a SKU are ordered, from 1 to 999. |
| **Money** | Value object | An immutable `BigDecimal` amount paired with an ISO-4217 `Currency`. It removes binary rounding errors and refuses to add different currencies. |
| **ShippingAddress** | Value object | Where the order is delivered: street, city, postal code and country. |
| **OrderStatus** | Value object (enum) | The lifecycle: `DRAFT -> PLACED -> PAID`, and `CANCELLED` from any non-final state. |
| **Draft** | Status | The customer is still building the order. Line items can be added only now. |
| **Place** (an order) | Command | The customer commits to the order. Requires at least one line item. Emits `OrderPlaced`. |
| **Pay** | Command | Payment for a placed order was confirmed. |
| **Cancel** | Command | The order is abandoned. Allowed from `DRAFT`, `PLACED` or `PAID`. Emits `OrderCancelled`. |
| **Order total** | Derived value | The sum of every line subtotal (unit price x quantity). Never stored, always calculated. |
| **Order limit** | Invariant | An order total may not exceed 10,000.00 in the order currency. |
| **Order currency** | Invariant | Every line item of an order uses the order currency. |
| **OrderPlaced** | Domain event | The fact that an order was placed: order id, customer id, order total, time. |
| **OrderCancelled** | Domain event | The fact that an order was cancelled, and from which status. |

### Business invariants (enforced by the `Order` aggregate)

1. Line items can be added only while the order is `DRAFT`.
2. Every line item uses the order currency.
3. The order total never exceeds 10,000.00 (in the order currency). A rejected change leaves the order untouched.
4. An order cannot be placed without line items.
5. Status changes follow `DRAFT -> PLACED -> PAID`. `CANCELLED` is reachable from every non-final state, and `CANCELLED` is final.
6. Adding a SKU that is already on the order increases that line's quantity, and the merged quantity must still be within 1..999.

### Terms that do *not* belong to this context

- **Stock level, reservation, warehouse**: Inventory context.
- **Charge, capture, refund, card**: Payment context.
- **Product name, description, image**: Catalogue. The order keeps only the SKU and the price agreed at checkout.
