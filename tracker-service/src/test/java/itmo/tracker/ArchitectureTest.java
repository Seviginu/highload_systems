package itmo.tracker;

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
                    .importPackages("itmo.tracker");

    @Test
    void domainDependsOnlyOnJavaAndDomain() {
        classes()
                .that()
                .resideInAPackage("itmo.tracker.domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "itmo.tracker.domain..")
                .check(CLASSES);
    }

    @Test
    void applicationDependsOnlyOnInnerLayers() {
        classes()
                .that()
                .resideInAPackage("itmo.tracker.application..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "itmo.tracker.domain..", "itmo.tracker.application..")
                .check(CLASSES);
    }

    @Test
    void adaptersDoNotDependOnConfigurationOrOtherAdapters() {
        noClasses()
                .that()
                .resideInAPackage("itmo.tracker.adapter..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("itmo.tracker.configuration..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage("itmo.tracker.adapter.in..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("itmo.tracker.adapter.out..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage("itmo.tracker.adapter.out..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("itmo.tracker.adapter.in..")
                .check(CLASSES);
    }
}
