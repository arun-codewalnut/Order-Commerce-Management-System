package com.example.commerce;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.example.commerce")
class ArchitectureTest {

    @ArchTest
    static final ArchRule legacy_technical_packages_are_removed = noClasses()
            .should().resideInAnyPackage(
                    "com.example.commerce.controller..",
                    "com.example.commerce.service..",
                    "com.example.commerce.repository..",
                    "com.example.commerce.entity..",
                    "com.example.commerce.dto..",
                    "com.example.commerce.util..",
                    "com.example.commerce.config..",
                    "com.example.commerce.exception..",
                    "com.example.commerce.scheduler..");

    @ArchTest
    static final ArchRule entities_are_private_to_capability_internals = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAnyPackage("com.example.commerce.*.internal..");

    @ArchTest
    static final ArchRule web_adapters_are_in_web_packages = classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAnyPackage("com.example.commerce.*.web..", "com.example.commerce.shared.web..");

    @ArchTest
    static final ArchRule field_injection_is_forbidden = noFields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Component.class)
            .should().beAnnotatedWith(Autowired.class);

    @ArchTest
    static void shared_does_not_depend_on_business_capabilities(JavaClasses classes) {
        noClasses().that().resideInAnyPackage("com.example.commerce.shared..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.example.commerce.customer..", "com.example.commerce.catalog..",
                        "com.example.commerce.inventory..", "com.example.commerce.order..",
                        "com.example.commerce.payment..", "com.example.commerce.notification..")
                .check(classes);
    }
}
