# Order Service

A backend service for a simple e-commerce shop: create orders, manage their
lifecycle (`CREATED → PAID → SHIPPED → DELIVERED`, or `CANCELLED`), and list
them with pluggable ordering rules.

## Versions

- **Java:** 21 (LTS)
- **Spring Boot:** 3.3.4
- **Build tool:** Maven
- **Persistence:** H2, file-based (`./data/orderdb`), so data survives restarts

## Build, run, test

```bash
# Run the full test suite
mvn test

# Run the service (starts on http://localhost:8080)
mvn spring-boot:run

# Or build a jar and run it
mvn package -DskipTests
java -jar target/order-service-1.0.0.jar
```

### With Docker

```bash
docker build -t order-service .
docker run -p 8080:8080 order-service
```

> Note on this submission's build environment: the sandbox used to write this
> code has no network access to Maven Central, so `mvn test` could not be
> executed here. The code was reviewed manually for compile-correctness; run
> `mvn test` in a normal environment to verify.

## API

Base path: `/api/orders`

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/orders` | Create an order |
| GET | `/api/orders/{id}` | Get one order |
| GET | `/api/orders?page=&size=&sort=` | List orders, paginated + sorted |
| PUT | `/api/orders/{id}` | Update customer name / line items |
| DELETE | `/api/orders/{id}` | Delete an order |
| POST | `/api/orders/{id}/pay` | Transition to PAID |
| POST | `/api/orders/{id}/ship` | Transition to SHIPPED |
| POST | `/api/orders/{id}/deliver` | Transition to DELIVERED |
| POST | `/api/orders/{id}/cancel` | Transition to CANCELLED (body: `{"reason": "..."}`) |

Sort keys currently supported by `?sort=`: `newest` (default), `highestTotal`,
`oldestUnpaidFirst`.

### Example requests

```bash
# Create
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Andi Wijaya",
    "items": [
      { "productName": "Apple", "quantity": 3, "unitPrice": 0.50 },
      { "productName": "Bread Loaf", "quantity": 1, "unitPrice": 2.20 }
    ]
  }'
# -> 201 Created, Location: /api/orders/{orderId}, totalAmount: 3.70, status: CREATED

# Read
curl http://localhost:8080/api/orders/{id}
curl http://localhost:8080/api/orders/{unknown-id}   # -> 404, ErrorResponse body

# List, highest value first
curl "http://localhost:8080/api/orders?page=0&size=20&sort=highestTotal"

# Lifecycle
curl -X POST http://localhost:8080/api/orders/{id}/pay
curl -X POST http://localhost:8080/api/orders/{id}/ship      # 409 if not yet PAID
curl -X POST http://localhost:8080/api/orders/{id}/deliver
curl -X POST http://localhost:8080/api/orders/{id}/cancel \
  -H "Content-Type: application/json" -d '{"reason": "Customer changed their mind"}'

# Update (only allowed on items while status is CREATED)
curl -X PUT http://localhost:8080/api/orders/{id} \
  -H "Content-Type: application/json" \
  -d '{"customerName": "Andi W.", "items": [{"productName":"Apple","quantity":5,"unitPrice":0.50}]}'

# Delete
curl -X DELETE http://localhost:8080/api/orders/{id}
```

### Error format

Every 4xx/5xx response has the same shape:

```json
{
  "timestamp": "2026-07-07T10:00:00Z",
  "status": 409,
  "error": "ILLEGAL_STATUS_TRANSITION",
  "message": "Cannot transition order from CREATED to SHIPPED",
  "path": "/api/orders/.../ship",
  "details": []
}
```

`details` is only populated for `VALIDATION_ERROR` (one string per invalid field).

## Design Decisions

### Data model / assumptions

- **`totalAmount` is server-computed, always.** The request DTOs
  (`OrderRequest`, `OrderItemRequest`) have no `totalAmount` field at all —
  it's derived from `sum(quantity * unitPrice)` on every create/update. This
  removes the entire "total that doesn't match line items" failure case by
  construction, and closes the "client sets totalAmount directly" security
  concern in the same move.
- **`orderId`, `status`, `createdAt`, `updatedAt` are never accepted from the
  client.** They're absent from the request DTOs, and
  `spring.jackson.deserialization.fail-on-unknown-properties: true` turns any
  attempt to send them into a clean `400 MALFORMED_REQUEST` instead of a
  silent no-op. This directly covers the "requests attempting to set
  orderId/status/totalAmount" security concern.
- **Money as `BigDecimal(precision=19, scale=2)`.** Avoids float rounding
  errors; 2 decimal places assumes a single currency (not modeled — out of
  scope, noted below).
- **Quantity must be ≥ 1, unitPrice must be > 0.** Rejects negative/zero
  values at the DTO validation layer (`400 VALIDATION_ERROR`), before they
  ever reach business logic.
- **An order must have at least one item** (`@NotEmpty` on `items`).

### How Part 2 changed the Part 1 design

Part 2 is why status changes are **not** a generic `PUT /orders/{id}` with a
`status` field, and why sorting is **not** a single hardcoded `ORDER BY`:

- **Status transitions are separate endpoints** (`/pay`, `/ship`, `/deliver`,
  `/cancel`), each backed by one line in an `ALLOWED_TRANSITIONS` map in
  `OrderService`. This exists specifically because "cancel needs a reason,
  others don't" — bolting that onto one shared PATCH payload means every
  future rule needs its own optional field and its own conditional
  validation on top of everyone else's. As a separate endpoint + DTO, a new
  transition with its own required data is one new method, one new map
  entry, one new DTO — nothing else changes.
- **Sorting uses the Strategy pattern** (`OrderSortStrategy` interface,
  one class per rule — `NewestFirstSortStrategy`,
  `HighestTotalFirstSortStrategy`, `OldestUnpaidFirstSortStrategy` — collected
  automatically into `SortStrategyRegistry` via Spring's list-of-beans
  injection). Adding a new ordering rule means adding one new
  `@Component` class; the controller, service, and registry are untouched.
  This was made a first-class abstraction *because* the spec says new rules
  will keep arriving.
- **Items are locked once `PAID`.** `OrderService.updateOrder` diffs the
  incoming items against the stored ones and rejects the update
  (`400 INVALID_ORDER`) if they differ and the order is no longer `CREATED`.
  `customerName` can still change at any status, since only items were
  specified as immutable.

### Sorting implementation note

`oldestUnpaidFirst` isn't expressible as a single-column SQL `ORDER BY` (it's
"unpaid orders first, then everything else, each oldest-first"). Rather than
push simple rules into the DB and special-case the complex one, all three
strategies are implemented as an in-memory `Comparator<Order>` applied after
`findAll()`, with pagination done manually over the sorted list. This is a
deliberate trade-off for a domain this size (dozens–thousands of orders, not
millions); at real scale I'd move to a `Specification`/derived-column
approach for the simple rules and keep only the composite ones in-memory.

### Optional assessments attempted

**Security** and **Architecture** (see above for both — fail-on-unknown
fields plus server-computed total covers the security ask; the transition
map + Strategy-pattern sorting covers architecture/extensibility). Also
included: a `Dockerfile` for straightforward containerized build/run
(lightweight take on the Deployment option), and a generic `500 INTERNAL_ERROR`
handler that never leaks stack traces or exception class names to the client.

### Scope deliberately omitted

- **Auth/access control** — no login, tenancy, or per-customer authorization;
  every order is visible to every caller. Called out under Security as a gap,
  not solved, given the 1–2 evening budget.
- **Currency handling** — single implicit currency, no multi-currency support.
- **Concurrency control** — no optimistic locking (`@Version`) on `Order`;
  two concurrent status transitions could race. Would add `@Version` first
  if extending this further.
- **DB-level sort scaling** — see note above.

### What I'd improve with more time

- Add `@Version` for optimistic locking on concurrent status transitions.
- Move `oldestUnpaidFirst`-style composite rules to proper `Specification`
  queries with DB-level pagination once data volume matters.
- Add integration tests (`@SpringBootTest` + `MockMvc`) on top of the current
  service-layer unit tests, to cover the HTTP/serialization layer end to end.
- Real authN/authZ per customer.
