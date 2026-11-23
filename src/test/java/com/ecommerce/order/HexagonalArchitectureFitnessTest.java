package com.ecommerce.order;

import com.ecommerce.order.application.OrderApplicationService;
import com.ecommerce.order.domain.event.DomainEvent;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Fitness functions for ADR-0006: the Order context is a hexagon.
 * <pre>
 *   adapter.in.web / adapter.in.event --> domain.port.in  --> application --> domain.model / domain.event
 *                                                                          --> domain.port.out <-- adapter.out.*
 * </pre>
 */
@AnalyzeClasses(packages = "com.ecommerce.order", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureFitnessTest {

    private static final String DOMAIN = "com.ecommerce.order.domain..";

    /** The rule from the lab handout: no infrastructure, Spring or JPA in the domain. */
    @ArchTest
    static final ArchRule domainMustBePure = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAPackage("com.ecommerce.order.infrastructure..")
            .orShould().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..");

    /** Stronger than the handout: the domain may use only the JDK and itself, so no library can leak in. */
    @ArchTest
    static final ArchRule domainDependsOnlyOnTheJdk = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN);

    @ArchTest
    static final ArchRule dependenciesPointIntoTheHexagon = onionArchitecture()
            .domainModels("com.ecommerce.order.domain.model..", "com.ecommerce.order.domain.event..")
            .domainServices("com.ecommerce.order.domain.port..")
            .applicationServices("com.ecommerce.order.application..")
            .adapter("web", "com.ecommerce.order.infrastructure.adapter.in.web..")
            .adapter("event-listener", "com.ecommerce.order.infrastructure.adapter.in.event..")
            .adapter("persistence", "com.ecommerce.order.infrastructure.adapter.out.persistence..")
            .adapter("event-publisher", "com.ecommerce.order.infrastructure.adapter.out.event..")
            .adapter("export", "com.ecommerce.order.export..");

    @ArchTest
    static final ArchRule packagesAreFreeOfCycles = slices()
            .matching("com.ecommerce.order.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule portsAreInterfaces = classes()
            .that().resideInAPackage("com.ecommerce.order.domain.port..")
            .and().areTopLevelClasses()
            .should().beInterfaces();

    @ArchTest
    static final ArchRule outboundAdaptersImplementAnOutboundPort = classes()
            .that().resideInAPackage("com.ecommerce.order.infrastructure.adapter.out..")
            .and().areTopLevelClasses()
            .should().implement(resideInAPackage("com.ecommerce.order.domain.port.out.."));

    @ArchTest
    static final ArchRule drivingAdaptersUseTheInboundPortNotTheImplementation = noClasses()
            .that().resideInAPackage("com.ecommerce.order.infrastructure.adapter.in..")
            .should().dependOnClassesThat().areAssignableTo(OrderApplicationService.class);

    @ArchTest
    static final ArchRule drivingAdaptersNeverReachOutboundPorts = noClasses()
            .that().resideInAPackage("com.ecommerce.order.infrastructure.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("com.ecommerce.order.domain.port.out..");

    @ArchTest
    static final ArchRule domainEventsAreImmutableRecords = classes()
            .that().implement(DomainEvent.class)
            .should().beAssignableTo(Record.class);

    @ArchTest
    static final ArchRule domainModelHasNoPublicSetters = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("com.ecommerce.order.domain.model..")
            .and().arePublic()
            .should().haveNameMatching("set[A-Z].*");
}
