# AGENTS.md — Engineering Standards & Agent Guide

This repository contains **commerce-api** (Order & Commerce Management System). This guide defines core standards and operational workflows for all AI coding agents and engineers.

---

## 1. Project Overview

`commerce-api` is a Spring Boot monolithic backend managing core e-commerce capabilities: Customers, Catalog, Inventory, Orders, Payments, and Notifications.

The codebase is undergoing refactoring from legacy technical layers (`controller/`, `service/`, `repository/`) into an enterprise modular architecture partitioned by business capability.

---

## 2. Tech Stack

- **Runtime & Language**: Java 21 LTS
- **Framework**: Spring Boot 3.3.4 (Web, Data JPA, Security, Validation, Cache, Actuator)
- **Database**: PostgreSQL 16 (Primary) with Flyway migrations
- **Testing**: JUnit 5, Mockito, Testcontainers (PostgreSQL 16), ArchUnit
- **Build & Containers**: Apache Maven 3.9+, Docker & Docker Compose
- **Documentation**: Springdoc OpenAPI 2.6.0 (Swagger UI)

---

## 3. Repository Structure

All business logic is organized strictly by business capability following the **3-root module rule**:

```text
com.example.commerce
├── <capability>/            # e.g. customer, catalog, inventory, order, payment, notification
│   ├── api/                 # Published contracts: interfaces & records other modules may import
│   ├── web/                 # HTTP adapters: controllers, request/response records, mappers
│   └── internal/            # Private domain: services, @Entity, repositories, business logic
└── shared/                  # Central kernel: errors, money, typed IDs (NO module dependencies)
```

- **`*.api`**: The **only** package other modules may import.
- **`*.web`**: HTTP adapters. Never leaks `@Entity`.
- **`*.internal`**: Private implementation. Strictly invisible across modules.
- **`shared/` Kernel**: May be imported by any module, but `shared/` may **never** import any business capability module.

---

## 4. Development Commands

```bash
# Build & package without tests
mvn clean package -DskipTests

# Run application locally
mvn spring-boot:run

# Run all tests
mvn clean test

# Run architectural verification tests
mvn test -Dtest=ArchitectureTest

# Start PostgreSQL and API service with Docker Compose
docker compose up --build -d
docker compose down
```

---

## 5. Coding Conventions

- **Naming**: Packages singular (`order`); Requests `<UseCase>Request`; Responses `<Thing>Response`; Services `<Verb><Noun>Service`; Events past tense (`OrderPlaced`).
- **Dependency Injection**: **Constructor injection only** (`private final` fields). Field injection (`@Autowired`) is strictly banned.
- **Immutability**: Use Java `record`s for all DTOs, Commands, Results, and Configuration.
- **Strongly Typed IDs**: Wrap primitive IDs (`CustomerId(Long value)`, `OrderId(Long value)`).
- **Lombok Rules**: **Never use `@Data` on JPA `@Entity`**. Use `@Getter`, `@Setter` (if mutable), and explicit business-key `equals`/`hashCode`.
- **Time & Money**: Inject `Clock` (never call `Instant.now()` in logic). Store UTC `Instant`. Represent currency as `BigDecimal` or typed `Money` (never `double`/`float`).

---

## 6. Architecture Rules

- **Module Isolation**: Package by business capability with exactly 3 package roots (`api`, `web`, `internal`). Modules communicate exclusively via published `*.api` contracts (N1, N2).
- **Type Separation**: Persistence types and HTTP types never mix. `@Entity` never leaves `internal` (N3). Web vocabulary (`HttpStatus`, `ResponseEntity`) stops at `web` (N4).
- **Kernel Admission**: `shared/` is reserved for stable, non-business code used by 2+ modules (N6).
- **Zero Cycles**: No cyclic dependencies between modules (ArchUnit) or beans.
- **Document Decisions**: Moving files between modules or creating a module requires an ADR in `docs/decisions/` (N7, 10.4).

> 📖 **Deep-Dive Skill**: For module design, the 4-rung boundary ladder, refactoring matrices, and ADR templates, activate [`.agents/skills/architecture/SKILL.md`](file:///d:/Projects/Order%20&%20Commerce%20Management%20System/.agents/skills/architecture/SKILL.md).
> 📖 **Deep-Dive Skill**: For module design, the 4-rung boundary ladder, refactoring matrices, and ADR templates, activate [`.agents/skills/architecture/SKILL.md`](.agents/skills/architecture/SKILL.md).

---

## 7. Testing Rules

- **Layer-Appropriate Testing**: Pure JUnit 5 + Mockito for domain services (<10ms); `@WebMvcTest` with `@Import(ApiExceptionHandler.class)` for controllers; Testcontainers PostgreSQL for repositories.
- **Negative Authorization**: Mandatory test verifying Customer A cannot access Customer B's resources.
- **Outcome Testing**: Assert on returned values and state changes, not collaborator implementation details (`verify()`).
- **ArchUnit Enforcement**: Structural boundaries (N2, N3, N4, 1.5, 8.3) are automated build failures in `ArchitectureTest.java`.

> 📖 **Deep-Dive Skill**: For test patterns, mock setups, and the complete ArchUnit suite, activate [`.agents/skills/testing/SKILL.md`](file:///d:/Projects/Order%20&%20Commerce%20Management%20System/.agents/skills/testing/SKILL.md).
> 📖 **Deep-Dive Skill**: For test patterns, mock setups, and the complete ArchUnit suite, activate [`.agents/skills/testing/SKILL.md`](.agents/skills/testing/SKILL.md).

---

## 8. Security Rules

- **Deny by Default**: Every endpoint is secured by default; public endpoints are explicitly permit-listed (N15).
- **Context-Derived Identity**: The actor identity comes exclusively from `@AuthenticationPrincipal`, never from request bodies or parameters (N14). Queries enforce ownership; missing/unowned records return `404`.
- **Anti-Mass-Assignment**: Never bind request bodies to `@Entity`. Use strict request records as allowlists (N3).
- **Injection Defense**: Parameterize all queries; allowlist dynamic sort columns.
- **Hardening & Limits**: Actuator exposes only `health` and `info`. Disable stack traces in responses. Disable Jackson polymorphic typing and enforce payload ceilings (`@Size`).

> 📖 **Deep-Dive Skill**: For security filter configs, authorization flows, and parser limits, activate [`.agents/skills/security/SKILL.md`](file:///d:/Projects/Order%20&%20Commerce%20Management%20System/.agents/skills/security/SKILL.md).
> 📖 **Deep-Dive Skill**: For security filter configs, authorization flows, and parser limits, activate [`.agents/skills/security/SKILL.md`](.agents/skills/security/SKILL.md).

---

## 9. Database Rules

- **Transaction Boundaries**: **Zero remote I/O inside `@Transactional`** (N8). Transactions start at the service layer, never in controllers. Never call proxied methods on `this`.
- **OSIV Disabled**: `spring.jpa.open-in-view=false`. Resolve N+1 queries using two-step fetching (`Slice` of IDs + `JOIN FETCH`).
- **Versioned Migrations**: All schema changes ship as forward-only Flyway migrations; `spring.jpa.hibernate.ddl-auto=validate` everywhere (N11).
- **Concurrency & Indexing**: Use `@Version` for optimistic locking. New queries must ship with corresponding Flyway indexes.

> 📖 **Deep-Dive Skills**: For transaction patterns, outbox design, and the Golden Checkout Path, activate [`.agents/skills/development/SKILL.md`](file:///d:/Projects/Order%20&%20Commerce%20Management%20System/.agents/skills/development/SKILL.md) and [`.agents/skills/resilience/SKILL.md`](file:///d:/Projects/Order%20&%20Commerce%20Management%20System/.agents/skills/resilience/SKILL.md).
> 📖 **Deep-Dive Skills**: For transaction patterns, outbox design, and the Golden Checkout Path, activate [`.agents/skills/development/SKILL.md`](.agents/skills/development/SKILL.md) and [`.agents/skills/resilience/SKILL.md`](.agents/skills/resilience/SKILL.md).

---

## 10. Agent Workflow

When implementing features or refactoring:
1. **Identify Capability**: Determine the owning capability folder or evaluate the 4-rung boundary ladder (Rule 10.1).
2. **Activate Specialized Skill**: Consult the relevant `.agents/skills/<skill>/SKILL.md` for in-depth runbooks, code examples, and edge-case handling.
3. **Enforce 3-Root Isolation**: Put public contracts in `api`, endpoints in `web`, logic and `@Entity` in `internal`.
4. **Draft ADR**: If introducing a module or moving files across modules, write an ADR in `docs/decisions/` (Rule 10.4).
5. **Write Layered Tests**: Write pure unit tests for domain logic, `@WebMvcTest` with negative auth for controllers, and verify `ArchitectureTest`.
6. **Verify Checklist**: Validate all criteria in the **Definition of Done** before completing the task.

---

## 11. Definition of Done

A task is complete only when:
- [ ] Code sits in the capability folder owning the behavior (1.1).
- [ ] No cross-module imports outside `*.api` packages (1.2).
- [ ] No `@Entity` appears in `web` or `api` signatures (2.2, 6.3).
- [ ] No `HttpStatus` or `ResponseEntity` below `web` (2.3).
- [ ] Errors throw domain `ErrorCode` mapped to HTTP status with a `traceId` (3.2).
- [ ] Money/stock mutations accept `Idempotency-Key` and follow the Golden Checkout Path (3.6, 5.6).
- [ ] Zero remote I/O inside `@Transactional` (4.1).
- [ ] Database queries ship with Flyway indexes; `ddl-auto` is `validate` (4.5, 7.5).
- [ ] Endpoints are denied by default; identity resolved from `@AuthenticationPrincipal` (6.1, 6.2).
- [ ] Remote calls have explicit timeouts; pools and queues are bounded (7.1, 7.2).
- [ ] Pure unit tests pass; negative authorization test is present (9.1).
- [ ] `mvn test -Dtest=ArchitectureTest` passes with zero violations (9.4).
- [ ] ADR exists in `docs/decisions/` if boundaries changed (10.4).

---

## 12. Important References

- **Architecture Skill**: [`.agents/skills/architecture/SKILL.md`](file:///d:/Projects/Order%20&%20Commerce%20Management%20System/.agents/skills/architecture/SKILL.md)
- **Development Skill**: [`.agents/skills/development/SKILL.md`](file:///d:/Projects/Order%20&%20Commerce%20Management%20System/.agents/skills/development/SKILL.md)
- **Security Skill**: [`.agents/skills/security/SKILL.md`](file:///d:/Projects/Order%20&%20Commerce%20Management%20System/.agents/skills/security/SKILL.md)
- **Testing Skill**: [`.agents/skills/testing/SKILL.md`](file:///d:/Projects/Order%20&%20Commerce%20Management%20System/.agents/skills/testing/SKILL.md)
- **Resilience Skill**: [`.agents/skills/resilience/SKILL.md`](file:///d:/Projects/Order%20&%20Commerce%20Management%20System/.agents/skills/resilience/SKILL.md)
- **Architecture Skill**: [`.agents/skills/architecture/SKILL.md`](.agents/skills/architecture/SKILL.md)
- **Development Skill**: [`.agents/skills/development/SKILL.md`](.agents/skills/development/SKILL.md)
- **Security Skill**: [`.agents/skills/security/SKILL.md`](.agents/skills/security/SKILL.md)
- **Testing Skill**: [`.agents/skills/testing/SKILL.md`](.agents/skills/testing/SKILL.md)
- **Resilience Skill**: [`.agents/skills/resilience/SKILL.md`](.agents/skills/resilience/SKILL.md)
