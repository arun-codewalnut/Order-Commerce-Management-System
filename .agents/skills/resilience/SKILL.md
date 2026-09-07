---
name: resilience
description: >-
  Resilience engineering, HTTP client timeouts, bounded thread pools, circuit breakers,
  cache hygiene, database indexing, observability signals, graceful shutdown, and compensation.
---

# Resilience Skill — Availability, Fault Tolerance & Performance

This skill establishes operational guidelines, fault tolerance configurations, and performance practices to ensure Spring Boot microservices survive downstream failures, traffic spikes, and network degradation.

---

## 1. Network Calls & Pool Boundaries

### Rule 7.1: Every Remote Call Has a Timeout on a Pooled Client (`MUST`, `N13`)
Java HTTP clients default to **waiting indefinitely**. An unresponsive third party (e.g. payment gateway) will park request threads forever, exhausting Tomcat's worker threads and bringing down unrelated endpoints.

1. **Explicit Timeouts**: Set both `connectTimeout` (e.g. 1s) and `readTimeout` (e.g. 2s).
2. **Client Pooling**: Use the JDK 11+ `HttpClient` or Apache `HttpClient5` with connection pooling enabled.
3. **Builder Isolation**: In Spring Boot 3.2+, the injected `RestClient.Builder` bean is a prototype template. Always call `.build()` or use a dedicated customizer to avoid mutating shared client configuration.
4. **Timeout Hierarchy**: Downstream timeout must fit inside the upstream caller's timeout budget (`caller_timeout > local_timeout + internal_processing`).

```java
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient paymentRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .version(HttpClient.Version.HTTP_2)
            .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));

        return RestClient.builder()
            .requestFactory(factory)
            .baseUrl("https://api.paymentgateway.internal")
            .build();
    }
}
```

---

### Rule 7.2: Bound Every Pool: Threads, Connections, Queues (`MUST`, `N13`)
Unbounded resources convert traffic spikes into out-of-memory crashes (`OutOfMemoryError`).
- **Spring Boot Footgun**: Auto-configured `applicationTaskExecutor` uses an **unbounded queue** (`LinkedBlockingQueue`). Tasks queue up in memory until the heap dies without triggering rejections.
- **Dedicated Executors**: Configure a named `ThreadPoolTaskExecutor` per workload with explicit queue capacity.
- **Rejection Policy**: Throw `RejectedExecutionException` and increment a metric counter. **Never use `CallerRunsPolicy` on HTTP request threads**, as it forces web threads to execute background work, destroying your web concurrency bulkhead.

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500); // STRICT BOUNDARY
        executor.setThreadNamePrefix("notification-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.setRejectedExecutionHandler((runnable, pool) -> {
            meterRegistry.counter("executor.rejected", "pool", "notification").increment();
            throw new RejectedExecutionException("Notification queue is saturated; task rejected.");
        });

        executor.initialize();
        return executor;
    }
}
```

---

## 2. Circuit Breakers, Retries & Degradation

### Rule 7.3: Retries Without Circuit Breakers Magnify Outages (`SHOULD`)
Retrying against a failing service triples incoming traffic, prolonging the outage.
- **Protection Order**: Timeout (7.1) ──▶ Bulkhead (concurrency ceiling) ──▶ Circuit Breaker (Resilience4j) ──▶ Retry (Last resort).
- **Circuit Breaker Behavior**: When failure threshold (e.g. 50%) is breached, the breaker opens, immediately short-circuiting calls with a fallback.
- **Retries**: Only retry transient failures (HTTP 503, connection reset, socket timeout). Never retry HTTP 400 Bad Request or validation errors. Use exponential backoff with jitter.

```java
@Service
@RequiredArgsConstructor
public class ExternalPaymentClient {

    private final RestClient paymentRestClient;

    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentGateway")
    public PaymentResponse capturePayment(PaymentRequest request) {
        return paymentRestClient.post()
            .uri("/v1/charges")
            .body(request)
            .retrieve()
            .body(PaymentResponse.class);
    }

    // Explicit fallback definition
    private PaymentResponse paymentFallback(PaymentRequest request, Throwable ex) {
        log.error("Payment gateway circuit open. Degraded response returned. OrderId={}", request.orderId(), ex);
        return PaymentResponse.declined("PAYMENT_SERVICE_DEGRADED");
    }
}
```

---

## 3. Caching & Database Performance

### Rule 7.4: Cache Deliberately with Explicit TTLs (`SHOULD`)
- **Key Uniqueness**: Cache keys **must** include the actor ID when caching user-specific data (`key = "#actor.customerId() + '-' + #id"`). Omitting the user from the key causes catastrophic data leakage across customer sessions.
- **Short TTLs Over Complex Invalidation**: Favor a 1-minute to 5-minute TTL over custom cache eviction hooks, which are prone to edge-case staleness bugs.
- **Self-Invocation Trap**: `@Cacheable` relies on Spring AOP. Invoking a cached method from within the same class bypasses the proxy and will not cache (Rule 4.3).

---

### Rule 7.5: Ship Indexes with Queries; Batch Database Writes (`MUST`)
1. **Query Ships with Index**: Every new `@Query` or finder method must have a corresponding Flyway migration adding the database index.
2. **PostgreSQL Non-Blocking Indexing**: On tables with millions of rows, use `CREATE INDEX CONCURRENTLY` in a standalone Flyway migration with transactional execution disabled:
   ```sql
   -- V4__add_order_customer_index.sql
   -- flyway:cleanDisabled=true
   CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_customer_status ON orders(customer_id, status);
   ```
3. **Batch Writes**: Never invoke `repository.save()` inside a loop. Use `saveAll()` and configure JDBC batching:
   ```properties
   spring.jpa.properties.hibernate.jdbc.batch_size=50
   spring.jpa.properties.hibernate.order_inserts=true
   spring.jpa.properties.hibernate.order_updates=true
   ```
4. **Batched Backfills**: When running data backfills, process in chunks (e.g. 500 rows) with committed watermarks rather than a single massive transaction that locks tables.

---

## 4. Observability & Graceful Teardown

### Rule 7.6: The 4 Golden Signals & Trace Propagation (`SHOULD`)
1. **The 4 Golden Signals**:
   - **Rate**: Requests per second (Micrometer timers).
   - **Errors**: Error count and ratio (5xx responses, domain `ErrorCode` counts).
   - **Duration**: Latency distributions using percentiles (`p95`, `p99`), **never averages**.
   - **Saturation**: Thread pool queue depth, HikariCP connection pool usage.
2. **Distributed Tracing**: Generate or propagate a `traceId` on all incoming requests. Store in SLF4J MDC and attach to outbound HTTP headers and error responses (Rule 3.2).
3. **Health Probes**:
   - **Liveness** (`/actuator/health/liveness`): Answers "is the JVM crashed?" (Do NOT check external databases here, or a DB outage will trigger rolling pod kills!).
   - **Readiness** (`/actuator/health/readiness`): Answers "can this pod take user traffic right now?"

---

### Rule 7.7: Graceful Shutdown (`SHOULD`)
A Kubernetes deploy is a pod termination. Abruptly killing pods drops in-flight HTTP requests and queued worker tasks.
```properties
# application.properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```
- During shutdown, the pod fails readiness first (Kubernetes removes it from the service endpoint pool).
- In-flight requests are allowed up to 30 seconds to finish processing before `SIGKILL`.

---

## 5. Distributed Compensation (Rule 10.5)

### When Remote Calls Succeed but Local Commits Fail
If an external payment gateway charges $100 but the local database transaction rolls back, or a timeout leaves the outcome unknown:
1. **Reserve, Then Confirm**: Whenever possible, authorize the card first. Authorizations expire safely if the local order creation fails. Capture only after the local order transaction commits.
2. **Compensation Outbox**: If direct capture is required, persist an outbox record (`PaymentCaptured`) **before** calling out, and derive an idempotency key from the order ID.
3. **Reconciler Worker**: A scheduled background job checks transactions stuck in `PENDING_PAYMENT` past 15 minutes, queries the external gateway API for transaction status, and triggers a refund or settles the order.

