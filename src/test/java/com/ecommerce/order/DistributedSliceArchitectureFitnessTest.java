package com.ecommerce.order;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Fitness functions for ADR-0007: the Order Service is a distributed slice with a REST contract
 * in {@code presentation.rest}, an event side-channel in {@code infrastructure.messaging},
 * and no code-level coupling to the rest of the monolith.
 */
@AnalyzeClasses(packages = {"com.ecommerce.order", "mn.edu.num.sad.ecommerce"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class DistributedSliceArchitectureFitnessTest {

    private static final String ORDER_SERVICE = "com.ecommerce.order..";
    private static final String REST = "com.ecommerce.order.presentation.rest..";
    private static final String DTO = "com.ecommerce.order.presentation.rest.dto..";
    private static final String MESSAGING = "com.ecommerce.order.infrastructure.messaging..";
    private static final String MONOLITH = "mn.edu.num.sad.ecommerce..";

    @ArchTest
    static final ArchRule controllersMustResideInPresentationRest = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .or().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(RestControllerAdvice.class)
            .should().resideInAPackage(REST);

    @ArchTest
    static final ArchRule dtosMustResideInDtoPackage = classes()
            .that(resideInAPackage(ORDER_SERVICE).and(
                    simpleNameEndingWith("Request").or(simpleNameEndingWith("Response")).or(simpleNameEndingWith("Dto"))))
            .should().resideInAPackage(DTO);

    @ArchTest
    static final ArchRule dtosAreImmutableRecords = classes()
            .that().resideInAPackage(DTO)
            .should().beRecords();

    @ArchTest
    static final ArchRule domainMustNotDependOnSpringWeb = noClasses()
            .that().resideInAPackage("com.ecommerce.order.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..");

    /** The wire format stays in the REST adapter: no other package may see request or response DTOs. */
    @ArchTest
    static final ArchRule restDtosDoNotLeakIntoTheService = noClasses()
            .that().resideInAPackage(ORDER_SERVICE)
            .and().resideOutsideOfPackage(REST)
            .should().dependOnClassesThat().resideInAPackage(REST);

    /** The REST adapter may not know that a side-channel exists, or the two could not fail independently. */
    @ArchTest
    static final ArchRule restAdapterDoesNotKnowTheSideChannel = noClasses()
            .that().resideInAPackage(REST)
            .should().dependOnClassesThat().resideInAnyPackage(MESSAGING, "com.ecommerce.order.infrastructure..");

    @ArchTest
    static final ArchRule sideChannelIsReachedOnlyThroughEvents = noClasses()
            .that().resideInAPackage(ORDER_SERVICE)
            .and().resideOutsideOfPackage(MESSAGING)
            .should().dependOnClassesThat().resideInAPackage(MESSAGING);

    @ArchTest
    static final ArchRule sideChannelDoesNotCallBackIntoUseCasesOrStorage = noClasses()
            .that().resideInAPackage(MESSAGING)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.ecommerce.order.application..",
                    "com.ecommerce.order.domain.port..",
                    "com.ecommerce.order.infrastructure.adapter..",
                    REST);

    /** A synchronous listener would run on the request thread and add its latency to the REST response. */
    @ArchTest
    static final ArchRule eventListenersAreAsynchronousAndLiveInMessaging = methods()
            .that().areAnnotatedWith(EventListener.class)
            .should().beAnnotatedWith(Async.class)
            .andShould().beDeclaredInClassesThat().resideInAPackage(MESSAGING);

    /** Service isolation: the slice and the remaining monolith share no classes, only (future) network contracts. */
    @ArchTest
    static final ArchRule orderServiceDoesNotDependOnTheMonolith = noClasses()
            .that().resideInAPackage(ORDER_SERVICE)
            .should().dependOnClassesThat().resideInAPackage(MONOLITH);

    @ArchTest
    static final ArchRule monolithDoesNotDependOnTheOrderService = noClasses()
            .that().resideInAPackage(MONOLITH)
            .should().dependOnClassesThat().resideInAPackage(ORDER_SERVICE);
}
