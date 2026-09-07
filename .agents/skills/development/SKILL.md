---
name: development
description: Procedural rules for writing, compiling, testing, and formatting Java & Spring Boot code in this project. Use this skill when generating controllers, services, repositories, DTOs, migrations, or tests.
description: Procedural rules for writing, compiling, testing, and hardening Java & Spring Boot applications in this project. Use this skill when generating controllers, services, repositories, DTOs, migrations, resilience configurations, or tests.
compatibility: Requires Java 21 LTS, Spring Boot 3.3+, and Maven 3.9+
metadata:
  version: "1.1"
  version: "1.2"
---

# Development & Testing Playbook
# Development, Resilience & Testing Playbook

## 1. Environment & Build Baseline
* **Java Version:** Target **Java 21 LTS** features (`record`s for immutable data, pattern matching, switch expressions).
* **Build Tool:** Apache Maven (`mvn`).
  * Verify compilation: `mvn clean compile`
  * Run test suite: `mvn clean test`
  * Verify architecture rules: `mvn test -Dtest=ArchitectureTest`
* **Database & Migrations:** PostgreSQL 16. All schema changes ship as forward-only Flyway migrations in `src/main/resources/db/migration/`. Hibernate DDL is locked: `spring.jpa.hibernate.ddl-auto=validate` everywhere.

## 2. Layer Boundaries & Type Flow
Adhere strictly to the capability-first 3-root structure (`api`, `web`, `internal`):
* **Controller (`web`):** Handles HTTP routing, deserialization, Bean Validation (`@Valid`), status codes, and DTO mapping.
* **Service (`internal`):** Encapsulates business logic, invariants, transaction boundaries (`@Transactional`), and authorization. Accepts `<UseCase>Command` records and returns `<UseCase>Result` records.
* **Repository (`internal`):** Database access via Spring Data JPA and projections.
* **Type Isolation (N3, N4):**
  - **`@Entity` never leaves `internal`:** Never return an `@Entity` from a controller or accept it as `@RequestBody`. Map queries to `Result` records, then map to `Response` DTOs in `web`.
  - **Web vocabulary stops at `web`:** `HttpStatus`, `ResponseEntity`, and `HttpServletRequest` are strictly forbidden in `internal` and `api`.
  - **Mappers live in the adapter (`web`):** Static factory methods (e.g. `OrderResponse.from(result)`) live in `web`, next to the DTOs they instantiate.

## 3. Contracts, Errors & Idempotency
* **Domain Error Codes (N5, 3.2):** Business failures throw `BusinessException(ErrorCode.<CODE>)`.
  - Define codes in `shared/errors/ErrorCode.java`.
  - Map codes to HTTP status in `shared/web/ErrorStatusMap.java`.
  - Always include a correlation `traceId` in error responses and log MDC.
* **Server-Controlled Pagination (3.4):**
  - Return `Slice<T>` over `Page<T>` to eliminate expensive `COUNT(*)` queries unless the UI explicitly requires total page counts.
  - Sort fields must be server-controlled and verified against a static allowlist (`Set<String>`) before query construction.
  - **Never** combine `Pageable` with `JOIN FETCH` collection associations (triggers in-memory pagination `HHH000104`).
* **Idempotency Keys (N9, 3.6):** All unsafe mutations (moving money, stock, or bookings) must require an `Idempotency-Key` header.
  - Scope keys to the authenticated actor (`key + actor.customerId()`).
  - Store request payload hash; return `409 Conflict` if the same key is reused with a different body.
  - Expire idempotency records after 24 hours.

## 4. Data, Transactions & Concurrency
* **Zero Remote I/O in Transactions (N8, 4.1):** Never make HTTP calls, broker publishes (`KafkaTemplate.send`), SMTP sends, or filesystem writes inside `@Transactional`.
* **Proxy Boundary (4.3):** Never call `@Transactional`, `@Async`, or `@Cacheable` methods on `this` within the same class (bypasses the Spring CGLIB proxy).
* **OSIV Disabled (4.4):** `spring.jpa.open-in-view=false` is mandatory. Solve lazy loading and N+1 queries using two-step fetching (`Slice` of IDs first, then `JOIN FETCH` for associations on that ID page).
* **Optimistic Locking (4.6):** Add `@Version private Long version;` on all mutable entities. For simple counters, use single-statement atomic SQL.
* **Transactional Outbox (5.1, 5.6):** Never use in-process events for stock or money. Commit an outbox row inside the local transaction, then drain and dispatch outside the transaction.
* **The Golden Checkout Path (5.6):**
  1. Transaction 1: Price items server-side, save order as `PENDING_PAYMENT`, reserve stock, insert outbox row (`CaptureRequested`), commit.
  2. Return HTTP `202 Accepted` (resource created but capture pending).
  3. Outbox worker executes payment gateway capture outside the transaction using an order-derived idempotency key.
  4. Transaction 2 records outcome (`PAID` or `PAYMENT_FAILED` which releases stock).
  5. Send confirmation emails via `@TransactionalEventListener(phase = AFTER_COMMIT)` on a named bounded executor.
  6. Scheduled Reconciler sweeps orders stuck in `PENDING_PAYMENT` past 15 minutes.
* **Distributed Compensation (10.5):** Prefer reserve-then-confirm over direct capture. If remote calls succeed but local commits fail, use the reconciliation worker to compare state with external provider APIs and issue automated refunds/releases.

## 5. Development Anti-Patterns ("Don'ts")
## 5. Resilience & Fault Tolerance (Part 7, N13)
* **Remote Timeouts on Pooled Clients (7.1, N13):** Every HTTP client (`RestClient`, `WebClient`) must configure explicit `connectTimeout` (e.g. 1s) and `readTimeout` (e.g. 2s) on a pooled client (`HttpClient`). Never rely on default indefinite timeouts.
* **Bounded Pools & Queues (7.2, N13):** Every `ThreadPoolTaskExecutor` must have explicit queue capacity (e.g. 500). Default auto-configured unbounded queues are forbidden.
  - Reject tasks with metric alerts on saturation.
  - **Never use `CallerRunsPolicy` on HTTP request threads** (destroys web thread bulkhead).
* **Circuit Breakers & Retries (7.3):** Protect external dependencies with Resilience4j circuit breakers. Always define an explicit fallback. Only retry transient failures (503, timeouts) with exponential backoff and jitter. Never retry 400 Bad Request or validation errors.
* **Deliberate Caching (7.4):** Cache keys must include the actor ID (`#actor.customerId() + '-' + #id`) when caching user-specific data to prevent cross-account data leaks. Favor short TTLs (1–5 min) over complex custom invalidation logic.
* **Database Indexing & Batching (7.5):** New database queries must ship with corresponding Flyway indexes. Use `CREATE INDEX CONCURRENTLY` for large tables. Never write row-by-row in loops; use `saveAll()` with `hibernate.jdbc.batch_size=50`.
* **Observability (7.6):** Track the 4 Golden Signals (Rate, Errors, Duration percentiles like p95/p99, Saturation). Propagate correlation `traceId` via MDC into logs and outbound headers.
* **Graceful Shutdown (7.7):** Enable `server.shutdown=graceful` with a 30s phase timeout so in-flight requests finish cleanly before pod termination.

## 6. Development Anti-Patterns ("Don'ts")
* **DO NOT use `@Data` on JPA Entities:** It generates equals/hashCode and toString over lazy associations and mutable IDs. Use `@Getter`, `@Setter` (if mutable), and explicit business-key equality.
* **DO NOT use field injection (`@Autowired`):** Use constructor injection only with `private final` fields (`@RequiredArgsConstructor` or explicit constructors).
* **DO NOT use `Instant.now()` or `LocalDate.now()` in logic:** Always inject `java.time.Clock` for testability (Rule 8.5). Store time as UTC `Instant`.
* **DO NOT use `double` or `float` for money:** Use `BigDecimal` with explicit scale and rounding mode, or a typed `Money` record (Rule 8.5).
* **DO NOT use raw primitives for IDs:** Wrap IDs in typed records (`CustomerId(Long value)`, `OrderId(Long value)`) to prevent accidental argument-swapping.
* **DO NOT bind request bodies directly to `@Entity`:** Prevents mass-assignment attacks (Rule 6.3). Use dedicated request records as allowlists.
* **DO NOT catch, log, and rethrow:** Throw typed domain exceptions and let the centralized `@RestControllerAdvice` in `shared/web/` translate them (Rule 8.1).
* **DO NOT use bare `@Async`:** Always specify a named, bounded executor (e.g. `@Async("notificationExecutor")`).

## 6. Testing & Verification Lifecycle
## 7. Testing & Verification Lifecycle
* **Ditch the 85% Blanket JaCoCo Target:** Do not write tests for every line of glue code, Lombok boilerplate, or framework wiring. Focus on the "Critical Few":
  1. **Pure domain logic unit tests (<10ms):** Cover business rules, pricing, calculations, and state invariants.
  2. **Negative authorization test per protected endpoint:** Verify Customer A cannot access Customer B's data (Rule 6.2).
  3. **ArchUnit structural verification:** Mechanical project-wide boundary enforcement in 500ms.

Every change must pass this layer-appropriate verification loop before creating a PR:
1. **Domain / Service Tests:**
   - Use pure **JUnit 5** and **Mockito** without Spring context (`@ExtendWith(MockitoExtension.class)`).
   - Execution time must be <10ms.
   - Assert on business rules, calculations, and invariant enforcement.
2. **Controller Tests (`@WebMvcTest`):**
   - Test routing, status codes, JSON serialization, and `@Valid` request shape validation.
   - Always `@Import(ApiExceptionHandler.class)` to verify domain error status mappings.
   - **Mandatory Negative Authorization Test:** Must verify that Customer A cannot read or modify Customer B's resources (Rule 6.2).
3. **Repository Tests (`@DataJpaTest`):**
   - Test against real PostgreSQL using Testcontainers: `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`.
   - Never rely on embedded H2 for repository query verification (Rule 9.2).
4. **Behavior-Driven Assertions:**
4. **Behavior-Driven Assertions (9.3):**
   - Assert on returned values, state changes, and emitted events. Avoid coupling tests to internal helper implementation details (`verify(times(1))`).
5. **Architectural Verification:**
   - Run `mvn test -Dtest=ArchitectureTest`.
   - Must pass with zero violations for module isolation (N2), entity containment (N3), web vocabulary boundaries (N4), cycle freedom (1.5), and constructor injection (8.3).
