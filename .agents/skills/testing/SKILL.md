---
name: testing
description: >-
  Layered testing strategies, unit testing without Spring, @WebMvcTest with negative authorization,
  PostgreSQL Testcontainers setup, and automated ArchUnit structural verification.
---

# Testing Skill — Testing Pyramid & Architectural Verification

This skill provides standards, test patterns, and code templates for building reliable, fast, and maintainable test suites in Java and Spring Boot.

---

## 1. Testing Pyramid & Layer Separation

### Rule 9.1: Test Each Layer for What That Layer Can Get Wrong (`MUST`)

| Layer / Target | Annotation / Strategy | Execution Speed | What it Validates |
|---|---|---|---|
| **Domain / Service** | Plain JUnit 5 + Mockito (Zero Spring Context) | ~1-5 ms | Business rules, calculations, invariant validation, state transitions. |
| **Controller (Web)** | `@WebMvcTest` + `@Import(ApiExceptionHandler.class)` | ~100-300 ms | Routing, JSON serialization, Bean Validation (`@Valid`), status codes, error mappings. |
| **Authorization** | `@WebMvcTest` + Security Context Mocking | ~100-300 ms | **Negative authorization**: Proving Customer A cannot read or modify Customer B's resources (Rule 6.2). |
| **Repository** | `@DataJpaTest` + Testcontainers PostgreSQL | ~500-1000 ms | SQL query correctness, Flyway migrations, custom projections, database constraints. |
| **End-to-End** | `@SpringBootTest` (Few, critical paths only) | ~5-15 sec | Full bean wiring, filter chain integration, end-to-end checkout flow. |
| **Architecture** | ArchUnit (Rule 9.4) | ~500 ms | Module boundaries, layer isolation, package rules. |

---

### Layer Test Examples

#### 1. Service Layer Test (Pure JUnit 5 — No Spring Context)
```java
@ExtendWith(MockitoExtension.class)
class PlaceOrderServiceTest {

    @Mock private CatalogQuery catalogQuery;
    @Mock private OrderRepository orderRepository;
    @Mock private OutboxRepository outboxRepository;

    private PlaceOrderService service;

    @BeforeEach
    void setUp() {
        service = new PlaceOrderService(catalogQuery, orderRepository, outboxRepository);
    }

    @Test
    void shouldRejectOrderWhenProductIsNotFound() {
        CustomerId customerId = new CustomerId(1L);
        ProductId productId = new ProductId(99L);
        PlaceOrderCommand command = new PlaceOrderCommand(customerId, List.of(new OrderLineCommand(productId, 2)));

        when(catalogQuery.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.place(command))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCode.ORDER_NOT_FOUND);
    }
}
```

#### 2. Controller Layer Test with Negative Authorization (`@WebMvcTest`)
Always import the global exception advice (`@Import(ApiExceptionHandler.class)`); otherwise, Spring defaults to basic error handling, and domain error assertions will pass for the wrong reason.
```java
@WebMvcTest(OrderController.class)
@Import(ApiExceptionHandler.class) // Mandatory to test custom error codes
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private OrderQueryService orderQueryService;

    @Test
    void shouldReturn404WhenCustomerAccessesAnotherCustomersOrder() throws Exception {
        CustomerId loggedInUser = new CustomerId(42L);
        OrderId targetOrderId = new OrderId(101L);

        // Simulate ownership rejection in query service
        when(orderQueryService.findForCustomer(loggedInUser, targetOrderId))
            .thenThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Order not found"));

        mockMvc.perform(get("/api/orders/101")
                .with(user(new CustomerPrincipal(loggedInUser)))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
            .andExpect(jsonPath("$.traceId").exists());
    }
}
```

---

### Rule 9.2: Test Against Real Databases via Testcontainers (`SHOULD`)
H2 is not PostgreSQL. It silently accepts queries that fail in PostgreSQL and rejects queries that succeed in PostgreSQL.
- Disable default embedded database replacement: `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)`.
- Use a single shared static PostgreSQL container across test suites to avoid container startup overhead:

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private OrderRepository orderRepository;

    @Test
    void shouldFindOrdersByCustomerIdAndStatus() {
        CustomerId customerId = new CustomerId(1L);
        // Flyway migrations execute automatically on container startup
        var orders = orderRepository.findByCustomerIdAndStatus(customerId, OrderStatus.PENDING_PAYMENT);
        assertThat(orders).isEmpty();
    }
}
```

---

### Rule 9.3: Test the Behavior, Not the Implementation (`SHOULD`)
- Avoid asserting internal invocation counts (`verify(repo, times(1)).save(any())`) unless the side-effect itself is the exact requirement.
- Assert on observable outcomes: returned records, persisted entities, published events, or emitted error codes. Tests coupled to internal helper calls break during benign refactoring.

---

## 2. Automated Architecture Testing (ArchUnit)

### Rule 9.4: Structural Rules Must Be Build Failures (`MUST`)
Human reviewers miss architectural leaks during PR reviews. The ArchUnit suite mechanically validates 5 key architectural rules on every build.

### Production ArchUnit Test Suite (`ArchitectureTest.java`)
Place in `src/test/java/com/example/badcommerce/ArchitectureTest.java`:

```java
package com.example.badcommerce;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.example.badcommerce", importOptions = DoNotIncludeTests.class)
public class ArchitectureTest {

    private static final String ROOT_PACKAGE = "com.example.badcommerce";
    private static final String KERNEL_MODULE = "shared";

    /**
     * N2: A module reaches another module ONLY through its published .api package.
     * Exception: 'shared' is the kernel (any module may use shared; shared may use no module).
     */
    @ArchTest
    public static final ArchRule modules_talk_only_through_api = classes()
        .should(new ArchCondition<>("only depend on other modules via their .api package") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String fromModule = extractModuleName(javaClass);
                if (fromModule == null) return;

                javaClass.getDirectDependenciesFromSelf().forEach(dependency -> {
                    String toModule = extractModuleName(dependency.getTargetClass());
                    if (toModule == null || toModule.equals(fromModule)) return;

                    boolean isAllowed = toModule.equals(KERNEL_MODULE)
                        || dependency.getTargetClass().getPackageName().contains("." + toModule + ".api");

                    if (!isAllowed) {
                        String message = String.format("Illegal cross-module dependency: %s -> %s (reaches into internals instead of .api)",
                            javaClass.getName(), dependency.getTargetClass().getName());
                        events.add(SimpleConditionEvent.violated(javaClass, message));
                    }
                });

                // Enforce kernel constraint: shared/ cannot depend on any business capability module
                if (KERNEL_MODULE.equals(fromModule)) {
                    javaClass.getDirectDependenciesFromSelf().forEach(dependency -> {
                        String toModule = extractModuleName(dependency.getTargetClass());
                        if (toModule != null && !toModule.equals(KERNEL_MODULE)) {
                            String message = String.format("Kernel violation: shared module class %s depends on capability module %s",
                                javaClass.getName(), toModule);
                            events.add(SimpleConditionEvent.violated(javaClass, message));
                        }
                    });
                }
            }
        });

    /**
     * N3: Persistence types (@Entity) never leave internal.
     * Forbidden from appearing in .web or .api packages.
     */
    @ArchTest
    public static final ArchRule entities_never_leave_internal = noClasses()
        .that().resideInAnyPackage("..web..", "..api..")
        .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
        .because("JPA Entities must stay confined within *.internal to prevent leaking database shapes.");

    /**
     * N4: Web vocabulary never travels downward.
     * Forbidden in *.internal or *.api packages.
     */
    @ArchTest
    public static final ArchRule web_vocabulary_stays_in_web = noClasses()
        .that().resideInAnyPackage("..internal..", "..api..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework.web..",
            "org.springframework.http..",
            "jakarta.servlet.."
        )
        .because("Web types (HttpStatus, ResponseEntity) must never leak into internal domain services or API contracts.");

    /**
     * Rule 1.5: Slices (capability packages) must be free of circular dependencies.
     */
    @ArchTest
    public static final ArchRule no_module_cycles = slices()
        .matching(ROOT_PACKAGE + ".(*)..")
        .should().beFreeOfCycles()
        .because("Modules must form an acyclic dependency graph.");

    /**
     * Rule 8.3: No field injection anywhere.
     */
    @ArchTest
    public static final ArchRule no_field_injection = noFields()
        .should().beAnnotatedWith(Autowired.class)
        .because("Constructor injection is mandatory; field injection is forbidden.");

    private static String extractModuleName(JavaClass clazz) {
        String pkg = clazz.getPackageName();
        if (!pkg.startsWith(ROOT_PACKAGE + ".")) return null;
        String remaining = pkg.substring(ROOT_PACKAGE.length() + 1);
        int dotIdx = remaining.indexOf('.');
        return dotIdx > 0 ? remaining.substring(0, dotIdx) : remaining;
    }
}
```

---

## 3. Honest Automated Coverage Matrix

Be precise about what ArchUnit enforces and what still requires human review:

| Check / Rule | Enforced By |
|---|---|
| **N1 (3 package roots per module)** | Partially ArchUnit (fails if layout is invalid); verified in PR review |
| **N2 (Inter-module contract via `*.api`)** | **ArchUnit (`modules_talk_only_through_api`)** |
| **N3 (`@Entity` never in `web` or `api`)** | **ArchUnit (`entities_never_leave_internal`)** |
| **N4 (Web vocabulary stops at `web`)** | **ArchUnit (`web_vocabulary_stays_in_web`)** |
| **N5 (Domain `ErrorCode` + edge `ErrorStatusMap`)** | PR Review + Unit Test |
| **N6 (`shared/` admission test)** | PR Review |
| **N7 (ADR for module changes)** | CI Path Check (`docs/decisions/*.md`) |
| **N8 (No remote I/O in `@Transactional`)** | PR Review |
| **N9 (Idempotency keys on unsafe HTTP)** | Controller Unit Test & PR Review |
| **N10 (Single `@RestControllerAdvice`)** | PR Review |
| **N11 (Flyway migrations validate)** | Startup Validation (`ddl-auto=validate`) |
| **N12 (Secrets outside Git, typed config)** | Pre-commit Git Hook + CI Scanner |
| **N13 (Timeouts & bounded pools)** | PR Review |
| **N14 (Identity from security context)** | Security Review & Negative Auth Test |
| **N15 (Deny by default)** | Security Filter Chain Test |
| **1.5 (No module cycles)** | **ArchUnit (`no_module_cycles`)** |
| **8.3 (No field injection)** | **ArchUnit (`no_field_injection`)** |

