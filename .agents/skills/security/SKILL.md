---
name: security
description: >-
  Security hardening, deny-by-default authorization, identity resolution from security context,
  prevention of mass-assignment and injection, actuator lockdown, and input ceiling controls.
---

# Security Skill — Spring Security & Vulnerability Defense

This skill outlines mandatory security protocols, authorization flows, and defensive coding rules for backend services in Spring Boot.

---

## 1. Authentication & Endpoint Authorization

### Rule 6.1: Every Endpoint is Denied by Default (`MUST`, `N15`)
A permit-list you forget to update fails closed; a deny-list you forget to update fails open.
- The `SecurityFilterChain` **must** terminate with `.anyRequest().authenticated()`.
- Public routes (`/actuator/health`, `/auth/token`) must be explicitly permit-listed by name.
- Multiple filter chains must be explicitly prioritized using `@Order` and scoped with `.securityMatcher()`.

```java
// GOOD: Explicit permit-list, strict deny-by-default
@Bean
@Order(1)
public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/token").permitAll()
            .anyRequest().authenticated() // DENY ALL OTHER BY DEFAULT
        )
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable()) // Safe ONLY because authentication is purely token-in-header (Stateless)
        .build();
}
```

> **CSRF Condition**: Disabling CSRF is permitted **only** when APIs are strictly stateless and use token-in-header authentication (Bearer tokens). The moment session cookies are introduced, CSRF protection **must** be re-enabled for all state-changing routes (`POST`, `PUT`, `DELETE`).

---

### Rule 6.2: Actor Identity Comes from Security Context, Never Request Payloads (`MUST`, `N14`)
A `customerId` passed in a query parameter or JSON body is a claim made by an unverified caller.
- **Identity Resolution**: Resolve the current user exclusively via `@AuthenticationPrincipal` in the controller.
- **Service Ownership Check**: Pass the typed actor ID into domain commands. The repository query checks both the resource ID and actor ID simultaneously.
- **Information Leakage**: Return `404 Not Found` (never `403 Forbidden`) when accessing another user's resource to prevent attackers from enumerating valid IDs.

```java
// BAD: Trusting the caller's query parameter
@GetMapping("/api/orders/{id}")
public OrderResponse getOrder(@RequestParam Long customerId, @PathVariable Long id) {
    return orderService.findOrder(id, customerId); // VIOLATION: Attacker changes ?customerId=42 to 43
}
```

```java
// GOOD: Verified actor from SecurityContext, ownership enforced in query
@GetMapping("/api/orders/{id}")
public OrderResponse getOrder(
        @AuthenticationPrincipal CustomerPrincipal actor,
        @PathVariable OrderId id) {
    OrderResult result = orderQueryService.findForCustomer(actor.customerId(), id);
    return OrderResponse.from(result);
}

// In internal/OrderQueryService.java:
public OrderResult findForCustomer(CustomerId customerId, OrderId id) {
    return orderRepository.findByIdAndCustomerId(id, customerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + id)); // Returns 404
}
```

---

## 2. Preventing Data Tampering & Injection

### Rule 6.3: Never Bind Request Bodies to `@Entity` (Mass Assignment) (`MUST`, `N3`)
Jackson will populate any entity field present in the JSON payload, and Hibernate will persist it.
- Never accept an entity class as a `@RequestBody` parameter.
- Request DTO records act as **strict allowlists**: only fields declared on the DTO can be modified.
- Never include sensitive system-managed fields (e.g. `role`, `id`, `balance`, `creditLimit`, `emailVerified`) in client request DTOs.

```java
// BAD: Direct entity binding enables privilege escalation
@PutMapping("/api/customers/{id}")
public void updateCustomer(@PathVariable Long id, @RequestBody Customer customer) {
    // If payload contains {"role": "ADMIN", "creditLimit": 1000000}, Hibernate saves them!
    customerRepository.save(customer); 
}
```

```java
// GOOD: Dedicated request record allows only editable fields
public record UpdateCustomerProfileRequest(
    @NotBlank @Size(max = 100) String fullName,
    @Email String contactEmail
) {} // 'role' is impossible to bind

@PutMapping("/api/customers/{id}")
public void updateCustomer(
        @AuthenticationPrincipal CustomerPrincipal actor,
        @PathVariable CustomerId id,
        @Valid @RequestBody UpdateCustomerProfileRequest request) {
    customerService.updateProfile(request.toCommand(actor.customerId(), id));
}
```

---

### Rule 6.4: Parameterize All Queries; Allowlist Identifiers (`MUST`)
- Bind all values using `@Param` or JDBC placeholders (`?`). Never concatenate raw strings into JPQL, HQL, or SQL.
- For dynamic parameters that cannot be bound via SQL placeholders (such as sort column names or sort directions), use a strict static allowlist (`Set<String>`).

```java
// BAD: SQL/JPQL Injection
@Query("SELECT o FROM Order o WHERE o.reference = '" + ref + "'") // VIOLATION
List<Order> findByRef(String ref);
```

```java
// GOOD: Parameterized value and allowlisted identifier
@Query("SELECT o FROM Order o WHERE o.reference = :ref")
List<Order> findByRef(@Param("ref") String ref);

private static final Set<String> ALLOWED_SORTS = Set.of("placedAt", "totalPrice");
if (!ALLOWED_SORTS.contains(sortField)) {
    throw new BusinessException(ErrorCode.INVALID_SORT_FIELD, "Unauthorized sort column: " + sortField);
}
```

---

## 3. Surface Lockdown & Resource Ceilings

### Rule 6.5: Close Accidental Doors (`MUST`)

| Surface | Hardening Requirement |
|---|---|
| **Actuator** | Expose only `health` and `info` publicly. Move sensitive endpoints (`heapdump`, `env`, `beans`) to a separate management port (`management.server.port=8081`) blocked from public routing. Never configure `endpoints.web.exposure.include="*"`. |
| **Stack Traces** | Configure `server.error.include-stacktrace=never` and `server.error.include-message=never`. Internal class names and framework versions must not leak. |
| **CORS** | Define explicit allowed origins. Never use `allowedOrigins("*")` alongside `allowCredentials(true)`. |
| **OpenAPI / Swagger** | Disable in production (`springdoc.swagger-ui.enabled=false`) or place behind management authentication. |
| **Security Headers** | Enforce HSTS, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and strict CSP. |

---

### Rule 6.6: Input Ceilings & Parser Constraints (`MUST`)
Unbounded inputs result in memory exhaustion (Denial of Service) or remote code execution.
1. **Never Enable Polymorphic Deserialization**: Do not use `ObjectMapper.enableDefaultTyping()`. If polymorphism is strictly required, use an explicit `@JsonSubTypes` annotation on an enum/sealed hierarchy you control.
2. **Cap Body & File Uploads**:
   ```properties
   spring.servlet.multipart.max-file-size=5MB
   spring.servlet.multipart.max-request-size=10MB
   ```
3. **Cap Document Depth**: Use Jackson's `StreamReadConstraints` to set maximum nesting depth (e.g. 100 levels) and max string length (e.g. 5,000,000 characters).
4. **Reject Unknown Properties**: Configure `spring.jackson.deserialization.fail-on-unknown-properties=true` so unexpected fields trigger immediate `400 Bad Request`.
5. **Validate Collection Bounds**: Use Bean Validation on all list fields: `@Size(min = 1, max = 50) List<OrderItemRequest> items`.

---

## 4. Supply Chain & Data Privacy

### Rule 6.7: Dependency Security (`SHOULD`)
- Use automated dependency scanners (Dependabot or Renovate) to keep dependencies patched weekly.
- Upgrade the Spring Boot parent BOM (`spring-boot-starter-parent`) rather than overriding individual transitive libraries.
- Run CVE scans (OWASP Dependency-Check or Snyk) in the CI pipeline; fail the build on `HIGH` or `CRITICAL` findings.

---

### Rule 6.8: Personal Data (PII) is Radioactive (`SHOULD`)
- **Never Log PII**: Strip email addresses, phone numbers, customer names, passwords, and tokens from logs, trace spans, and metric labels.
- **Separate External and Internal IDs**: Internal database primary keys should use sequential `BIGINT` or time-ordered UUIDv7/ULID; external IDs presented to users should be opaque tokens to prevent enumeration attacks.

