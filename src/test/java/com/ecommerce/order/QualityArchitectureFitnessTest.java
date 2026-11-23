package com.ecommerce.order;

import com.ecommerce.order.domain.port.out.PaymentPort;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Fitness functions for ADR-0008: resilience tactics are infrastructure concerns. They stay in
 * {@code infrastructure.resilience}, and the domain, the use cases and the REST adapter never see Resilience4j.
 */
@AnalyzeClasses(packages = "com.ecommerce.order", importOptions = ImportOption.DoNotIncludeTests.class)
class QualityArchitectureFitnessTest {

    private static final String RESILIENCE = "com.ecommerce.order.infrastructure.resilience..";
    private static final String RESILIENCE4J = "io.github.resilience4j..";

    /** The handout rule, applied to methods: Resilience4j annotations are placed on methods, not classes. */
    @ArchTest
    static final ArchRule resilienceAnnotationsMustStayInInfrastructure = methods()
            .that().areAnnotatedWith(CircuitBreaker.class)
            .or().areAnnotatedWith(RateLimiter.class)
            .or().areAnnotatedWith(Bulkhead.class)
            .should().beDeclaredInClassesThat().resideInAPackage(RESILIENCE);

    @ArchTest
    static final ArchRule domainMustNotDependOnResilience4j = noClasses()
            .that().resideInAPackage("com.ecommerce.order.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(RESILIENCE4J);

    /** The use case only knows the PaymentPort; how a call is protected is invisible to it. */
    @ArchTest
    static final ArchRule useCasesMustNotKnowResilienceTactics = noClasses()
            .that().resideInAPackage("com.ecommerce.order.application..")
            .should().dependOnClassesThat().resideInAnyPackage(RESILIENCE4J, RESILIENCE);

    @ArchTest
    static final ArchRule onlyTheResiliencePackageUsesResilience4j = noClasses()
            .that().resideInAPackage("com.ecommerce.order..")
            .and().resideOutsideOfPackage(RESILIENCE)
            .should().dependOnClassesThat().resideInAnyPackage(RESILIENCE4J);

    /** Every adapter for the payment gateway lives next to its protection, so none can bypass it unnoticed. */
    @ArchTest
    static final ArchRule paymentAdaptersLiveInTheResiliencePackage = classes()
            .that().implement(PaymentPort.class)
            .should().resideInAPackage(RESILIENCE);

    /** The blocking gateway client may only be called by the payment adapters, never by the use case or the web. */
    @ArchTest
    static final ArchRule gatewayClientIsReachedOnlyThroughThePaymentAdapters = noClasses()
            .that().resideInAPackage("com.ecommerce.order..")
            .and().resideOutsideOfPackage(RESILIENCE)
            .should().dependOnClassesThat().resideInAPackage("com.ecommerce.order.infrastructure.resilience.gateway..");
}
