package itmo.notification;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private static final JavaClasses CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("itmo.notification");

    @Test
    void domainDependsOnlyOnJavaAndDomain() {
        classes()
                .that()
                .resideInAPackage("itmo.notification.domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "itmo.notification.domain..")
                .check(CLASSES);
    }

    @Test
    void applicationDependsOnlyOnInnerLayers() {
        classes()
                .that()
                .resideInAPackage("itmo.notification.application..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "java..", "itmo.notification.domain..", "itmo.notification.application..")
                .check(CLASSES);
    }

    @Test
    void adaptersDoNotDependOnConfigurationOrOtherAdapters() {
        noClasses()
                .that()
                .resideInAPackage("itmo.notification.adapter..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("itmo.notification.configuration..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage("itmo.notification.adapter.in..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("itmo.notification.adapter.out..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage("itmo.notification.adapter.out..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("itmo.notification.adapter.in..")
                .check(CLASSES);
    }
}
