package com.ecommerce.order;

import com.ecommerce.order.application.OrderRepository;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.ecommerce.order", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureLayerEnforcementTest {

    @ArchTest
    static final ArchRule layersPointInward = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Presentation").definedBy("com.ecommerce.order.presentation..")
            .layer("Application").definedBy("com.ecommerce.order.application..")
            .layer("Domain").definedBy("com.ecommerce.order.domain..")
            .layer("Infrastructure").definedBy("com.ecommerce.order.infrastructure..")
            .layer("Export").definedBy("com.ecommerce.order.export..")
            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Export").mayNotBeAccessedByAnyLayer()
            .whereLayer("Export").mayOnlyAccessLayers("Domain")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation", "Infrastructure")
            .whereLayer("Application").mayOnlyAccessLayers("Domain")
            .whereLayer("Domain").mayNotAccessAnyLayer();

    @ArchTest
    static final ArchRule packagesAreFreeOfCycles = slices()
            .matching("com.ecommerce.order.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule exportDependsOnlyOnDomain = classes()
            .that().resideInAPackage("com.ecommerce.order.export..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", "com.ecommerce.order.export..", "com.ecommerce.order.domain..");

    @ArchTest
    static final ArchRule domainIsPureJava = classes()
            .that().resideInAPackage("com.ecommerce.order.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.ecommerce.order.domain..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnSpring = noClasses()
            .that().resideInAPackage("com.ecommerce.order.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..");

    @ArchTest
    static final ArchRule infrastructureImplementsApplicationPort = classes()
            .that().resideInAPackage("com.ecommerce.order.infrastructure..")
            .and().haveSimpleNameEndingWith("Repository")
            .should().implement(OrderRepository.class);

    @ArchTest
    static final ArchRule portsLiveInApplicationLayer = classes()
            .that().haveSimpleNameEndingWith("Repository").and().areInterfaces()
            .should().resideInAPackage("com.ecommerce.order.application..");

    @ArchTest
    static final ArchRule controllersDoNotTouchTheRepository = noClasses()
            .that().resideInAPackage("com.ecommerce.order.presentation..")
            .should().dependOnClassesThat().areAssignableTo(OrderRepository.class);
}
