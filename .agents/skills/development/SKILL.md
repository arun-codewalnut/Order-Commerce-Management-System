---
name: development
description: >-
  Day-to-day coding practices, DTO/Entity isolation, API contracts, transaction boundaries,
  outbox patterns, the Golden Checkout Path, error handling, and clean Spring Boot development.
---

# Development Skill — Spring Boot & Java Standards

This skill provides step-by-step procedures, coding conventions, and architectural patterns for daily implementation tasks in Java 21 and Spring Boot 3.3+.

---

## 1. Layer Responsibilities & Data Flow

### Rule 2.1: Each Layer Owns Exactly One Job (`MUST`)

| Layer | Package | Responsibilities | Forbidden / Never Allowed |
|---|---|---|---|
| **Controller** | `*.web` | Routing, deserialization, Bean Validation (`@Valid`), status codes, response mapping. | Business rules, database transactions, entity manipulation. |
| **Service** | `*.internal` | Use-case execution, transaction boundary (`@Transactional`), business invariants, authorization checks. | HTTP types (`ResponseEntity`, `HttpStatus`), JSON annotations, `HttpServletRequest`, direct `SecurityContextHolder` access. |
| **Repository** | `*.internal` | Persistence queries, row-to-entity mapping, projections. | Business logic, response shaping, web vocabulary. |

```text
HTTP Request ──▶ PlaceOrderRequest (web)
                      │  .toCommand(actorId)
                      ▼
                 PlaceOrderCommand (internal)
                      │  placeOrderService.place(cmd)
                      ▼
                 PlaceOrderResult (internal record)
                      │  OrderResponse.from(result)
                      ▼
HTTP Response ◀── OrderResponse (web)
```

---

### Rule 2.2: Persistence Types and HTTP Types Are Never the Same (`MUST`, `N3`)
An entity's shape is a promise to the database; a DTO is a promise to a client.
- `@Entity` classes must **never** be named in `web` or `api` packages.
- Never place `@Transient`, `@JsonProperty`, or `@JsonIgnore` on an entity to cater to UI screens.
- **Outbound**: Query records or entities map to a domain `Result` record, which maps to a `Response` DTO in the `web` layer.
- **Inbound**: Request DTOs convert to typed domain `Command` records.

```java
// BAD: Entity returned directly to the HTTP client
@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    @GetMapping("/{id}")
    public Customer getCustomer(@PathVariable Long id) { // VIOLATION: Leaking persistence entity
        return customerRepository.findById(id).orElseThrow();
    }
}
```

```java
// GOOD: Separation through Result records and Web Response DTOs
// 1. In customer.internal:
public record CustomerResult(CustomerId id, String fullName, boolean adult) {}

// 2. In customer.web:
public record CustomerResponse(String id, String fullName, boolean adult) {
    public static CustomerResponse from(CustomerResult result) {
        return new CustomerResponse(result.id().value(), result.fullName(), result.adult());
    }
}
```

---

### Rule 2.3: Web Vocabulary Travels Upward, Never Downward (`MUST`, `N4`)
Types such as `ResponseEntity`, `HttpStatus`, and servlet headers belong strictly in `*.web`.
- If an internal service throws an exception or returns a value containing `HttpStatus`, it cannot be safely called by a batch job, message consumer, or unit test without pulling web dependencies.
- Enforced mechanically by ArchUnit (Rule 9.4).

---

### Rule 2.4: Mappers Live in the Adapter (`SHOULD`)
The adapter knows both worlds. Static factory methods (e.g. `OrderResponse.from(result)`) or dedicated mappers belong in `*.web`, next to the DTOs they instantiate.

---

## 2. API Contracts & External World

### Rule 3.2: Domain Error Codes with Edge Status Mapping (`MUST`, `N5`)
Never return generic `500 Internal Server Error` without domain context. Never couple domain exceptions to `HttpStatus`.
1. **Domain `ErrorCode`**: Resides in `shared/errors/ErrorCode.java`. Framework-free enum with stable, machine-readable keys.
2. **Edge Mapping `ErrorStatusMap`**: Resides in `shared/web/ErrorStatusMap.java`. Maps `ErrorCode` to HTTP statuses.
3. **Trace ID**: Every error response **must** include a `traceId` for observability.

```java
// shared/errors/ErrorCode.java (Domain)
public enum ErrorCode {
    ORDER_NOT_FOUND,
    ORDER_ALREADY_PAID,
    PAYMENT_DECLINED,
    INSUFFICIENT_STOCK,
    INVALID_SORT_FIELD,
    INTERNAL_ERROR
}

// shared/web/ErrorStatusMap.java (Edge Adapter)
public final class ErrorStatusMap {
    private static final Map<ErrorCode, HttpStatus> MAP = Map.of(
        ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND,
        ErrorCode.ORDER_ALREADY_PAID, HttpStatus.CONFLICT,
        ErrorCode.PAYMENT_DECLINED, HttpStatus.UNPROCESSABLE_ENTITY,
        ErrorCode.INSUFFICIENT_STOCK, HttpStatus.UNPROCESSABLE_ENTITY,
        ErrorCode.INVALID_SORT_FIELD, HttpStatus.BAD_REQUEST,
        ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR
    );

    public static HttpStatus resolve(ErrorCode code) {
        return MAP.getOrDefault(code, HttpStatus.INTERNAL_SERVER_ERROR); // Never use get() without default
    }
}
```

---

### Rule 3.4: Server-Controlled Paginated Collection Endpoints (`MUST`)
Unbounded queries crash production systems via memory exhaustion.
1. **Server Controls Sort Columns**: Allowlist sort fields to prevent SQL injection (6.4).
2. **Use `Slice<T>` over `Page<T>`**: `Page` executes an expensive `COUNT(*)` query on every page. Return `Slice` unless the UI explicitly requires total count.
3. **Never combine `Pageable` with `JOIN FETCH` collections**: Hibernate warns `HHH000104` and paginates in memory! Use two-step fetching (Rule 4.4).

```java
private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("placedAt", "total", "status");

@GetMapping("/orders")
public PageResponse<OrderResponse> listOrders(
        @AuthenticationPrincipal CustomerPrincipal actor,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "placedAt") String sort) {

    if (!ALLOWED_SORT_FIELDS.contains(sort)) {
        throw new BusinessException(ErrorCode.INVALID_SORT_FIELD, "Invalid sort field: " + sort);
    }

    PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
    Slice<OrderResult> slice = orderQueryService.listForCustomer(actor.customerId(), pageRequest);
    return PageResponse.of(slice.map(OrderResponse::from));
}
```

---

### Rule 3.6: Idempotency Keys on Unsafe Requests (`MUST`, `N9`)
Network timeouts cause client retries. Any endpoint that moves money or stock must accept an `Idempotency-Key` header:
- Scope keys to the actor: `key + actor.customerId()`.
- Store a SHA-256 hash of the request body. If the key is reused with a different payload, return `409 Conflict`.
- Expire idempotency records after 24 hours.

```java
@PostMapping("/orders")
public ResponseEntity<OrderResponse> placeOrder(
        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
        @AuthenticationPrincipal CustomerPrincipal actor,
        @Valid @RequestBody PlaceOrderRequest request) {

    PlaceOrderResult result = idempotencyService.runOnce(
        idempotencyKey, 
        actor.customerId(), 
        request.payloadHash(), 
        () -> placeOrderService.place(request.toCommand(actor.customerId()))
    );

    return ResponseEntity.accepted().body(OrderResponse.from(result));
}
```

---

## 3. Data & Transactions

### Rule 4.1: No Remote I/O Inside Database Transactions (`MUST`, `N8`)
Holding a database transaction during remote calls starves the connection pool and holds row locks.
- **Banned inside `@Transactional`**: External HTTP calls, Kafka/RabbitMQ message publishing (`KafkaTemplate.send`), SMTP email sending, filesystem I/O, `Thread.sleep()`.
- **Allowed inside `@Transactional`**: Local database writes (including transactional outbox inserts) and Spring in-memory `publishEvent()`.

---

### Rule 4.3: Never Call Proxied Methods on `this` (`MUST`)
Spring proxies intercept calls from other beans. If method A in Bean X calls `@Transactional` method B in Bean X directly, the proxy is bypassed and **no transaction starts**.
- Inject the collaborating service or split into separate beans.

---

### Rule 4.4: Disable Open-Session-In-View (OSIV) & Fix N+1 Queries (`MUST`)
Configure `spring.jpa.open-in-view=false`. To prevent `LazyInitializationException` and N+1 query storms, use **two-step fetching**:
1. Page the IDs.
2. Fetch the associations for that bounded ID page using `JOIN FETCH`.

```java
// Step 1: Page IDs only
@Query("SELECT o.id FROM Order o WHERE o.status = :status")
Slice<Long> findOrderIds(@Param("status") OrderStatus status, Pageable pageable);

// Step 2: Fetch collection associations for those specific IDs
@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items WHERE o.id IN :ids")
List<Order> findWithItems(@Param("ids") Collection<Long> ids);
```

---

### Rule 4.5: Schema Changes Ship as Versioned Migrations (`MUST`, `N11`)
- Set `spring.jpa.hibernate.ddl-auto=validate` in all environments.
- Use Flyway with forward-only migrations under `src/main/resources/db/migration/V{version}__{description}.sql`.
- Deploys are non-atomic: breaking column renames require 3 rollout phases (Add column -> dual-write & backfill -> drop old column).

---

### Rule 4.6: Concurrency Protection via `@Version` (`SHOULD`)
Add `@Version private Long version;` to all mutable entities to catch concurrent overwrites via `OptimisticLockException`. For simple counters, use atomic SQL (`UPDATE inventory SET stock = stock - 1 WHERE id = :id AND stock >= 1`).

---

## 4. Async, Events & The Golden Checkout Path

### Rule 5.1 & 5.2: Durability Selection & Event Handling (`MUST`)
- **Tolerable Loss** (metrics, cache clearing): In-process `@EventListener`.
- **Recoverable Loss** (order confirmation emails): `@TransactionalEventListener(phase = AFTER_COMMIT)` on a **named, bounded executor**.
- **Zero Loss** (stock reservation, payment capture, ledger): **Transactional Outbox** row committed inside the business transaction.

---

### Rule 5.3: ShedLock on Multi-Instance `@Scheduled` Tasks (`MUST`)
Without distributed locking, a scheduled job runs concurrently on every pod replica.
```java
@Scheduled(cron = "0 0 2 * * *")
@SchedulerLock(name = "paymentReconciliation", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
public void reconcileNightly() {
    reconciliationService.reconcile();
}
```

---

### Rule 5.4: Idempotent Event Processing with Conditional Inserts (`MUST`, `N9`)
Consumers must claim events in the database before processing:
```java
@Transactional
public void process(OrderPlaced event) {
    int claimed = jdbcTemplate.update(
        "INSERT INTO processed_events (event_id, processed_at) VALUES (?, NOW()) ON CONFLICT (event_id) DO NOTHING",
        event.eventId()
    );
    if (claimed == 0) {
        log.debug("Event already processed: eventId={}", event.eventId());
        return; // Duplicate ignored safely
    }
    inventoryRepository.decrementStock(event.lines());
}
```

---

### Rule 5.6: The Golden Checkout Path Blueprint (`MUST`, `N9`)
When handling money, stock, or bookings, implement this exact 6-step coordination architecture:

```text
Step 1: Local Transaction
        - Price items server-side via CatalogQuery.
        - Save Order with status PENDING_PAYMENT.
        - Reserve stock locally if sharing datasource.
        - Insert Outbox row: CaptureRequested.
        - COMMIT.

Step 2: Return HTTP 202 Accepted
        - Order exists; client polls /api/orders/{id}.
        - (Only return 201 Created if resource is fully settled with no external dependencies).

Step 3: Outbox Drain (Outside Transaction)
        - Background worker reads CaptureRequested outbox row.
        - Executes Payment Gateway capture using order-derived Idempotency Key.

Step 4: Second Transaction
        - Outcome == CAPTURED ──▶ Mark Order PAID.
        - Outcome == DECLINED ──▶ Mark Order PAYMENT_FAILED and release stock reservation.
        - Outcome == UNKNOWN  ──▶ Leave PENDING_PAYMENT; let Reconciler resolve.

Step 5: In-Process Notification
        - Send confirmation email via AFTER_COMMIT event listener.

Step 6: Reconciliation Sweeper
        - Scheduled job queries orders still in PENDING_PAYMENT past 15-minute threshold.
        - Queries Payment Gateway for ground truth and settles or refunds (Rule 10.5).
```

---

## 5. Cross-Cutting Concerns

### Rule 8.1: Centralized Exception Handling (`MUST`, `N10`)
Never catch, log, and immediately rethrow exceptions. Handle them once at the edge:
```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        log.warn("Business rule violation: code={} message={}", ex.code(), ex.getMessage());
        HttpStatus status = ErrorStatusMap.resolve(ex.code());
        return ResponseEntity.status(status).body(new ApiError(ex.code(), ex.getMessage(), MDC.get("traceId")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        String traceId = MDC.get("traceId");
        log.error("Unhandled exception traceId={}", traceId, ex);
        return ResponseEntity.internalServerError()
            .body(new ApiError(ErrorCode.INTERNAL_ERROR, "An internal error occurred.", traceId));
    }
}
```

---

### Rule 8.2 & 8.3: Typed Configuration & Clean Constructors (`MUST`, `N12`)
- Group configuration into typed records annotated with `@ConfigurationProperties` and `@Validated`.
- Inject dependencies via constructor injection (`private final` fields with `@RequiredArgsConstructor` or explicit constructors). **Zero `@Autowired` on fields.**
- **Wrap IDs**: Use record wrappers like `CustomerId(Long value)` and `OrderId(Long value)` instead of bare primitives to prevent argument-swapping bugs.
- **Time & Money (Rule 8.5)**: Inject `java.time.Clock` instead of calling `Instant.now()`. Store time as UTC `Instant`. Represent currency amounts as `BigDecimal` with explicit scale and rounding mode, or use a typed `Money` record. Never use `double` or `float`.

