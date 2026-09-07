---
name: security
description: Mandatory security guardrails, deny-by-default authorization, context-derived identity, anti-mass assignment, query parameterization, and parser ceiling controls in this project.
compatibility: Requires Java 21 LTS and Spring Security 6+
metadata:
  version: "1.1"
---

# Security Playbook — Guardrails & Vulnerability Defense

## 1. Authentication & Endpoint Authorization (Rule 6.1, N15)
* **Deny by Default:** Every endpoint is secured by default. The `SecurityFilterChain` must terminate with `.anyRequest().authenticated()`.
* **Explicit Permit-Lists:** Public routes (`/actuator/health`, `/actuator/info`, `/auth/token`) must be individually permit-listed by name. Never use wildcards like `permitAll("/api/**")`.
* **CSRF Policy:** Disabled **only** when APIs are strictly stateless and use token-in-header authentication (Bearer tokens). The moment cookie or session authentication is used, CSRF protection is mandatory for all mutating routes (`POST`, `PUT`, `DELETE`).
* **FilterChain Ordering:** Multiple security filter chains must be explicitly scoped with `.securityMatcher()` and ordered using `@Order`.

## 2. Identity & Access Control (Rule 6.2, N14)
* **Context-Derived Identity:** The actor identity must come exclusively from `@AuthenticationPrincipal` or `SecurityContextHolder`, never from query parameters (e.g. `?customerId=42`) or request bodies.
* **Service-Level Ownership Enforcement:** Pass the verified actor ID into domain commands. The repository query must filter by both resource ID and actor ID (`findByIdAndCustomerId(id, actor)`).
* **Anti-Enumeration (404 over 403):** If a resource does not exist or belongs to another user, always return `404 Not Found`. Never return `403 Forbidden` for ID lookups, as it confirms the resource ID exists to an attacker.

## 3. Data Integrity & Injection Defense (Rules 6.3, 6.4, N3)
* **Anti-Mass Assignment (N3):** Never bind request bodies directly to JPA `@Entity` classes.
  - Inbound HTTP payloads must bind to dedicated `<UseCase>Request` records.
  - Request records act as strict allowlists: system-managed fields (`role`, `creditLimit`, `balance`, `emailVerified`, `id`, `createdAt`) must never appear on request DTOs.
* **Query Parameterization:** Bind all query values using named parameters (`:param`) or JDBC placeholders (`?`). String concatenation in SQL, JPQL, or HQL is strictly prohibited.
* **Identifier Allowlisting:** Dynamic sort columns and table names cannot be parameterized; they must be validated against a static allowlist (`Set<String>`) before query construction.

## 4. Attack Surface Lockdown (Rule 6.5)
* **Actuator Protection:** Expose only `health` and `info` publicly. Sensitive endpoints (`heapdump`, `env`, `beans`, `threaddump`) must reside on a separate internal management port (`management.server.port`) behind authentication. Never configure `management.endpoints.web.exposure.include="*"`.
* **Information Leakage:** Configure `server.error.include-stacktrace=never` and `server.error.include-message=never`. Error responses must return a stable `ErrorCode` and correlation `traceId` (Rule 3.2), never internal class names or database traces.
* **CORS Restrictions:** Configure explicit allowed origins. Never combine `allowedOrigins("*")` with `allowCredentials(true)`.
* **OpenAPI / Swagger:** Disable in production environments (`springdoc.swagger-ui.enabled=false`) or restrict access to the private management port.
* **Security Headers:** Enforce HSTS, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and strict CSP.

## 5. Input Ceilings & Parser Constraints (Rule 6.6)
* **No Polymorphic Deserialization:** Never enable Jackson default typing (`ObjectMapper.enableDefaultTyping()`). If subtype polymorphism is necessary, use explicit `@JsonSubTypes` declarations on a closed type hierarchy.
* **Payload Ceilings:** Strictly enforce request body and multipart upload limits:
  - `spring.servlet.multipart.max-file-size=5MB`
  - `spring.servlet.multipart.max-request-size=10MB`
* **Parser Constraints:** Limit JSON document nesting depth and max string length using Jackson's `StreamReadConstraints`.
* **Strict Deserialization:** Configure `spring.jackson.deserialization.fail-on-unknown-properties=true` so unexpected payload fields trigger an immediate `400 Bad Request`.
* **Collection Size Validation:** Enforce size bounds on all incoming list fields using Bean Validation: `@Size(min = 1, max = 50) List<OrderItemRequest> items`.

## 6. Secrets & Supply Chain Hygiene (Rules 6.7, 6.8, 8.2, N12)
* **Zero Secrets in Git:** Never commit API keys, private keys, passwords, or tokens. Configuration must be grouped in `@ConfigurationProperties` with values injected via deploy-time environment variables.
* **Supply Chain Scanning:** Run automated dependency updates (Dependabot/Renovate) weekly. Build pipelines must fail on `HIGH` or `CRITICAL` CVEs. Upgrade managed Spring Boot parent BOMs rather than pinning individual libraries.
* **PII Redaction:** Personal identifiable information (email, phone, real names, passwords, tokens) must never appear in application logs, MDC attributes, trace spans, or metric labels.
