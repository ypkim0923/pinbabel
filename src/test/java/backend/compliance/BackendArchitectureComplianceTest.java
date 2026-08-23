package backend.compliance;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

@AnalyzeClasses(
    packages = "com.ypkim.pinbabel",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class BackendArchitectureComplianceTest {

    private static final String ROOT_PACKAGE = "com.ypkim.pinbabel";
    private static final String ORCHESTRATION_SLICE = "orchestration";
    private static final Set<String> SHARED_SLICES = Set.of();

    @ArchTest
    static void domainMustBeFrameworkNeutral(JavaClasses classesToCheck) {
        noClasses()
            .that().resideInAPackage(ROOT_PACKAGE + "..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.servlet..",
                "jakarta.validation..",
                "com.fasterxml.jackson.."
            )
            .allowEmptyShould(true)
            .check(classesToCheck);
    }

    @ArchTest
    static void applicationMustNotDependOnAdapters(JavaClasses classesToCheck) {
        noClasses()
            .that().resideInAPackage(ROOT_PACKAGE + "..application..")
            .should().dependOnClassesThat().resideInAPackage(ROOT_PACKAGE + "..adapter..")
            .allowEmptyShould(true)
            .check(classesToCheck);
    }

    @ArchTest
    static void applicationAndDomainMustNotDependOnSecurityOrProviderSdks(
        JavaClasses classesToCheck
    ) {
        noClasses()
            .that().resideInAnyPackage(
                ROOT_PACKAGE + "..application..",
                ROOT_PACKAGE + "..domain.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.security..",
                "org.springframework.web..",
                "org.springframework.core.io..",
                "org.springframework.data..",
                "java.sql..",
                "javax.sql..",
                "jakarta.servlet..",
                "software.amazon.awssdk..",
                "com.amazonaws..",
                "com.auth0..",
                "com.okta..",
                "com.google.firebase.."
            )
            .allowEmptyShould(true)
            .check(classesToCheck);
    }

    @ArchTest
    static void controllersMustNotDependOnPersistence(JavaClasses classesToCheck) {
        noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAnyPackage(
                ROOT_PACKAGE + "..repository..",
                ROOT_PACKAGE + "..adapter.out.persistence..",
                "org.springframework.data..",
                "java.sql..",
                "javax.sql.."
            )
            .allowEmptyShould(true)
            .check(classesToCheck);
    }

    @ArchTest
    static void webDtosMustNotDependOnCoreOrPersistence(JavaClasses classesToCheck) {
        noClasses()
            .that().resideInAPackage(ROOT_PACKAGE + "..adapter.in.web.dto..")
            .should().dependOnClassesThat().resideInAnyPackage(
                ROOT_PACKAGE + "..application..",
                ROOT_PACKAGE + "..domain..",
                ROOT_PACKAGE + "..adapter.out.persistence..",
                "org.springframework.data..",
                "org.springframework.core.io..",
                "java.sql..",
                "javax.sql.."
            )
            .allowEmptyShould(true)
            .check(classesToCheck);
    }

    @ArchTest
    static void persistenceProjectionsMustRemainInsidePersistenceAdapter(
        JavaClasses classesToCheck
    ) {
        noClasses()
            .that().resideOutsideOfPackage(ROOT_PACKAGE + "..adapter.out.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage(
                ROOT_PACKAGE + "..adapter.out.persistence.projection..",
                ROOT_PACKAGE + "..adapter.out.persistence..projection.."
            )
            .allowEmptyShould(true)
            .check(classesToCheck);
    }

    @ArchTest
    static void webDtoNamesMustExpressTransportRole(JavaClasses classesToCheck) {
        classes()
            .that().resideInAPackage(ROOT_PACKAGE + "..adapter.in.web.dto..")
            .should(new ArchCondition<>("end with Request, Response, or Dto") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    String name = item.getSimpleName();
                    boolean valid = name.endsWith("Request")
                        || name.endsWith("Response")
                        || name.endsWith("Dto");
                    if (!valid) {
                        events.add(SimpleConditionEvent.violated(
                            item,
                            item.getFullName() + " does not express its Web transport role"
                        ));
                    }
                }
            })
            .allowEmptyShould(true)
            .check(classesToCheck);
    }

    @ArchTest
    static void businessSlicesMustNotDependOnOtherSlicesOrOrchestration(
        JavaClasses classesToCheck
    ) {
        classes()
            .that().resideInAPackage(ROOT_PACKAGE + "..")
            .should(new ArchCondition<>("respect Business Slice and Orchestration boundaries") {
                @Override
                public void check(JavaClass source, ConditionEvents events) {
                    String sourceSlice = sliceOf(source);
                    if (sourceSlice.isEmpty() || sourceSlice.equals(ORCHESTRATION_SLICE)) {
                        return;
                    }
                    source.getDirectDependenciesFromSelf().stream()
                        .map(dependency -> dependency.getTargetClass())
                        .filter(BackendArchitectureComplianceTest::isProjectType)
                        .filter(target -> isForbiddenCrossSlice(sourceSlice, sliceOf(target)))
                        .forEach(target -> events.add(SimpleConditionEvent.violated(
                            source,
                            source.getFullName() + " depends on forbidden Slice type "
                                + target.getFullName()
                        )));
                }
            })
            .check(classesToCheck);
    }

    private static boolean isForbiddenCrossSlice(String sourceSlice, String targetSlice) {
        if (sourceSlice.equals(targetSlice)) {
            return false;
        }
        if (targetSlice.isEmpty()) {
            return true;
        }
        return !SHARED_SLICES.contains(targetSlice);
    }

    private static boolean isProjectType(JavaClass javaClass) {
        return javaClass.getPackageName().startsWith(ROOT_PACKAGE + ".");
    }

    private static String sliceOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (packageName.equals(ROOT_PACKAGE) || !packageName.startsWith(ROOT_PACKAGE + ".")) {
            return "";
        }
        String relativePackage = packageName.substring(ROOT_PACKAGE.length() + 1);
        int separator = relativePackage.indexOf('.');
        return separator < 0 ? relativePackage : relativePackage.substring(0, separator);
    }
}
