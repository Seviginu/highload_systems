package itmo.user;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("itmo.user");

    @Test
    void domainDependsOnlyOnJavaAndDomain() {
        classes().that().resideInAPackage("itmo.user.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "itmo.user.domain..")
                .check(CLASSES);
    }

    @Test
    void applicationDependsOnlyOnInnerLayersAndReactor() {
        classes().that().resideInAPackage("itmo.user.application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "itmo.user.domain..",
                        "itmo.user.application..", "reactor..", "org.reactivestreams..")
                .check(CLASSES);
    }

    @Test
    void adaptersDoNotDependOnConfigurationOrOtherAdapters() {
        noClasses().that().resideInAPackage("itmo.user.adapter..")
                .should().dependOnClassesThat().resideInAPackage("itmo.user.configuration..").check(CLASSES);
        noClasses().that().resideInAPackage("itmo.user.adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("itmo.user.adapter.out..").check(CLASSES);
        noClasses().that().resideInAPackage("itmo.user.adapter.out..")
                .should().dependOnClassesThat().resideInAPackage("itmo.user.adapter.in..").check(CLASSES);
    }
}
