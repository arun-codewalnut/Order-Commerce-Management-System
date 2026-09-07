---
name: architecture
description: >-
  Architectural design, module boundary enforcement, package structuring,
  shared kernel management, decision ladders, ADR creation, and god-class decomposition.
---

# Architecture Skill — Structural Standards & Module Boundaries

This skill guides agents and engineers on architectural design, modular decomposition, packaging, inter-module dependency enforcement, and refactoring strategies in Spring Boot applications.

---

## 1. Core Architectural Rules

### Rule 1.1: Package by Business Capability, Not by Technical Layer (`MUST`, `N1`)
Top-level folders like `controller/`, `service/`, and `repository/` scale with the number of technical layers (3), whereas capability packages scale with the business.
Every business module has **exactly three package roots**:
1. **`api`**: Published contract. Interfaces, queries, commands, and summary records that other modules are allowed to import.
2. **`web`**: HTTP adapter. REST controllers, request/response records, and HTTP mappers. Invisible to other modules.
3. **`internal`**: Private implementation. Entities (`@Entity`), Spring Data repositories, internal domain services, and business invariants. Strictly off-limits to other modules.

```text
// BAD: Layer-first (collapses under growth)
com.example.badcommerce
├── controller/     (31 files)
├── service/        (44 files)
├── repository/
├── dto/
└── entity/

// GOOD: Capability-first (3 roots per module)
com.example.badcommerce
├── order/
│   ├── api/         OrderQuery, OrderSummary        ← other modules may use these
│   ├── web/         OrderController, PlaceOrderRequest, OrderResponse
│   └── internal/    PlaceOrderService, Order (@Entity), OrderRepository,
│                    PlaceOrderCommand, PlaceOrderResult
├── catalog/
│   ├── api/         CatalogQuery, ProductSummary
│   ├── web/         ProductController
│   └── internal/    Product (@Entity), ProductRepository, CatalogService
└── shared/          Kernel only — passes admission test
```

> **The Delete Test**: To remove a feature or business capability, you should be able to delete exactly one capability folder without breaking unrelated modules.

---

### Rule 1.2: Modules Reach Each Other Only Through Published Contracts (`MUST`, `N2`)
The moment `order` imports `catalog`'s repository or entity, catalog's database schema becomes order's problem.
- A module reaches another module **only** via its `*.api` package.
- No class outside a module may name any class inside that module's `*.internal` or `*.web` packages.
- Enforced mechanically by ArchUnit (Rule 9.4).

```java
// BAD: Order directly binds Catalog's internal persistence
package com.example.badcommerce.order.internal;

import com.example.badcommerce.catalog.internal.ProductRepository; // VIOLATION: reaches into internals

class PlaceOrderService {
    private final ProductRepository productRepository; 
}
```

```java
// GOOD: Order depends exclusively on Catalog's published API contract
package com.example.badcommerce.catalog.api;

public interface CatalogQuery {
    Optional<ProductSummary> findById(ProductId id);
}
public record ProductSummary(ProductId id, String name, Money price) {}

// In order/internal:
package com.example.badcommerce.order.internal;

import com.example.badcommerce.catalog.api.CatalogQuery; // LEGAL
import com.example.badcommerce.catalog.api.ProductSummary;

class PlaceOrderService {
    private final CatalogQuery catalog;
}
```

---

### Rule 1.3: `shared/` is a Kernel with an Admission Test (`MUST`, `N6`)
The `shared/` folder must never become a dumping ground for undecided abstractions. It is a **kernel**: every module may depend on it, but `shared/` may depend on **no** business module.

To place anything in `shared/`, it must pass all 3 questions of the **Admission Test**:
1. **Used by two or more modules right now?** (Not "might be tomorrow" — today).
2. **Free of business meaning?** (If changing it requires a product/business discussion, it belongs in a capability module).
3. **Stable?** (If it changes when a business rule changes, it is a business rule with a technical name).

If it passes all three, place it into a named sub-package:
- `shared/errors/` (`ErrorCode` enum, `BusinessException`)
- `shared/money/` (`Money` type, currency arithmetic)
- `shared/types/` (strongly typed ID records like `CustomerId`, `OrderId`)
- `shared/web/` (`ErrorStatusMap`, `PageResponse`, `ApiExceptionHandler`)

> **Standing Residents**: The `ErrorCode` catalog (3.2), `Money` type (8.5), and wrapped IDs (8.3) live in `shared/` by architectural decree. Adding any fourth resident requires an ADR (10.4).

---

### Rule 1.4: Strict Naming Conventions (`SHOULD`)
Standardized naming creates immediate clarity for both humans and coding agents:

| Artifact | Convention | Example |
|---|---|---|
| **Package** | singular, lowercase, business noun | `order`, not `orders` |
| **Inbound HTTP Type** | `<UseCase>Request` | `PlaceOrderRequest` |
| **Outbound HTTP Type** | `<Thing>Response` / `<Thing>Summary` | `OrderResponse`, `OrderSummary` |
| **Service Input** | `<UseCase>Command` | `PlaceOrderCommand` |
| **Service Output** | `<UseCase>Result` | `PlaceOrderResult` |
| **Use-case Service** | `<Verb><Noun>Service` | `PlaceOrderService` (avoid `OrderHelper`) |
| **Domain Event** | Past tense (it already occurred) | `OrderPlaced`, never `PlaceOrderEvent` |
| **Repository Query** | State intent, not SQL syntax | `findOpenOrdersFor(CustomerId)` |
| **Boolean Flag** | `is` / `has` / `can` prefix | `isEligibleForDiscount` |
| **Test Method** | `should<Outcome>When<Condition>` | `shouldRejectOrderWhenStockIsInsufficient` |
| **Configuration** | kebab-case under application namespace | `app.order.max-line-items` |

> **Red Flag Names**: Classes containing `Helper`, `Manager`, `Util`, `Processor`, or `Data` usually indicate an unformed boundary.

---

### Rule 1.5: No Cyclic Dependencies (`MUST`)
- **Module Cycles**: Slice cycles between capability packages are strictly forbidden and tested by ArchUnit (`slices().matching("com.example.badcommerce.(*)..").should().beFreeOfCycles()`).
- **Bean Cycles**: Circular Spring bean dependencies are disabled at startup via `spring.main.allow-circular-references=false`. Never bypass this with `@Lazy` or setter injection; invert the dependency using domain events (`@EventListener`) or extract shared contracts.

---

### Rule 2.5: Split Services by Use Case Early (`SHOULD`)
Avoid mammoth classes like `CommerceService` (which has thousands of lines).
Split by discrete use cases before line count forces an emergency refactor:
- `OrderService` becomes `PlaceOrderService`, `CancelOrderService`, `OrderQueryService`.
- Treat classes exceeding ~400 lines as an architectural signal to evaluate responsibilities.

---

## 2. Decision Frameworks ("When You're Stuck")

### 2.1 The 4-Rung Boundary Ladder (Rule 10.1)
When engineers disagree on which module owns a feature (e.g. "recent searches"):

```text
1. Whose data changes when this runs?
   └── Exactly one module writes → It lives there. (Done)
   └── Two or more modules write → Proceed to Rung 2.

2. Delete each candidate module in your imagination.
   └── Which survivor still needs this feature? → It belongs to that survivor. (Done)
   └── Both survivors still need it → Proceed to Rung 3.

3. Whose language does the feature speak?
   └── E.g., "Recent searches" is Search vocabulary, not Customer vocabulary → Belongs to Search. (Done)
   └── Language is genuinely tied → Proceed to Rung 4.

4. Assign it to the module that owns the DECISION, not the one that owns the data.
   └── Record the decision in docs/decisions/ (10.4).
   └── NEVER default to "it's ambiguous, so put it in shared/".
```

---

### 2.2 Refactoring Timing Matrix: Churn vs Defects (Rule 10.2)

Measure churn via `git log --oneline --since=3.months -- path/ | wc -l` and defect history from bug trackers:

```text
                     Rarely Changes                     Changes Constantly
            ┌──────────────────────────────────┬──────────────────────────────────┐
Breaks      │ REFACTOR NOW                     │ COORDINATE FIRST                 │
Often       │ Breaks often, low team churn.    │ Costing you twice over.          │
            │ Take 2-3 dedicated days.         │ Agree on a short freeze first;   │
            │ (Best refactoring ROI)           │ surprise refactors cause wars.   │
            ├──────────────────────────────────┼──────────────────────────────────┤
Breaks      │ LEAVE IT ALONE                   │ NOT NOW                          │
Rarely      │ Ugly but harmless. Not costing   │ High churn, low bugs. Refactoring│
            │ the business. Spend time elsewhere.│ causes merge conflicts, not quality.│
            │                                  │ Only improve what you touch.     │
            └──────────────────────────────────┴──────────────────────────────────┘
```

---

### 2.3 Taking Apart a 13,000-Line God Class (Rule 10.3)
Follow these 7 sequential steps strictly:
1. **Freeze behavior**: Write characterization integration tests asserting current behavior (even quirks) without altering code.
2. **Map it**: Catalog all public methods and tag each with the business capability it serves.
3. **Find the seams**: Group methods that access the same fields, repository queries, and tables.
4. **Extract the quietest capability first**: Extract the method cluster with the fewest external callers to validate the seam.
5. **Move, don't rewrite**: Copy methods to the new capability service and have the old god-class delegate to the new service.
6. **Ship it**: Merge and deploy each individual capability extraction in separate, small PRs.
7. **Repeat, then delete the shell**: Once all capabilities are extracted, the original god class becomes an empty delegator; delete it in the final PR.

---

### 2.4 Architecture Decision Records (ADRs) (Rule 10.4, `MUST`, `N7`)
**Mandatory Trigger**: Any Pull Request that adds a new module or moves a file between modules **must** include a new Markdown file in `docs/decisions/`.

**Template (`docs/decisions/NNNN-<title>.md`)**:
```markdown
# N. <Title>

Status:   Accepted | Deprecated | Superseded
Date:     YYYY-MM-DD
Deciders: <Names>

## Context
What problem are we solving? What candidate modules or solutions were considered?

## Decision
What is the chosen boundary, placement, or architecture?

## Why
Which rung of the ladder (10.1) or architectural justification drove this decision?

## Consequences
- What is easier now?
- What is harder or explicitly restricted?

## What We Rejected
What alternatives were rejected and why?
```

