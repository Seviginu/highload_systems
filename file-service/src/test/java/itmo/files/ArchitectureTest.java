package itmo.files;

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
                    .importPackages("itmo.files");

    @Test
    void domainDependsOnlyOnJavaAndDomain() {
        classes()
                .that()
                .resideInAPackage("itmo.files.domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "itmo.files.domain..")
                .check(CLASSES);
    }

    @Test
    void applicationDependsOnlyOnInnerLayers() {
        classes()
                .that()
                .resideInAPackage("itmo.files.application..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "itmo.files.domain..", "itmo.files.application..")
                .check(CLASSES);
    }

    @Test
    void adaptersDoNotDependOnConfigurationOrOtherAdapters() {
        noClasses()
                .that()
                .resideInAPackage("itmo.files.adapter..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("itmo.files.configuration..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage("itmo.files.adapter.in..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("itmo.files.adapter.out..")
                .check(CLASSES);
        noClasses()
                .that()
                .resideInAPackage("itmo.files.adapter.out..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("itmo.files.adapter.in..")
                .check(CLASSES);
    }
}
