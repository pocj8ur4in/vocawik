package com.vocawik.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Architecture guardrails for package boundaries and dependency direction. */
@AnalyzeClasses(
        packages = "com.vocawik",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchitectureTest {

    @ArchTest
    static final ArchRule controller_should_not_depend_on_repository =
            noClasses()
                    .that()
                    .resideInAPackage("com.vocawik.controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.vocawik.repository..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule service_should_not_depend_on_controller =
            noClasses()
                    .that()
                    .resideInAPackage("com.vocawik.service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.vocawik.controller..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule repository_should_not_depend_on_service_or_controller =
            noClasses()
                    .that()
                    .resideInAPackage("com.vocawik.repository..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.vocawik.service..", "com.vocawik.controller..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule dto_should_not_depend_on_domain =
            noClasses()
                    .that()
                    .resideInAPackage("com.vocawik.dto..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.vocawik.domain..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_dto =
            noClasses()
                    .that()
                    .resideInAPackage("com.vocawik.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.vocawik.dto..");

    @ArchTest
    static final ArchRule non_config_packages_should_not_depend_on_config =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("com.vocawik.config..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.vocawik.config..");
}
