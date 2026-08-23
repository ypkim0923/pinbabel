package com.ypkim.pinbabel.influenceranalysis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

@AnalyzeClasses(
	packages = "com.ypkim.pinbabel.influenceranalysis",
	importOptions = ImportOption.DoNotIncludeTests.class
)
class InfluencerAnalysisPackageArchitectureTest {

	private static final String SLICE_PACKAGE = "com.ypkim.pinbabel.influenceranalysis";

	@ArchTest
	static void sliceTypesStayInsideFullMappingPackages(JavaClasses classesToCheck) {
		classes()
			.that().resideInAPackage(SLICE_PACKAGE + "..")
			.should(new ArchCondition<>("reside below full-mapping application or adapter packages") {
				@Override
				public void check(JavaClass item, ConditionEvents events) {
					var packageName = item.getPackageName();
					var moduleDeclaration = item.getFullName().equals(SLICE_PACKAGE + ".package-info");
					var allowed = moduleDeclaration
						|| isAtOrBelow(packageName, SLICE_PACKAGE + ".application.domain")
						|| isAtOrBelow(packageName, SLICE_PACKAGE + ".application.port")
						|| isAtOrBelow(packageName, SLICE_PACKAGE + ".application.service")
						|| isAtOrBelow(packageName, SLICE_PACKAGE + ".adapter");
					if (!allowed) {
						events.add(SimpleConditionEvent.violated(
							item,
							item.getFullName() + " is outside the Slice full-mapping structure"
						));
					}
				}
			})
			.check(classesToCheck);
	}

	private static boolean isAtOrBelow(String packageName, String allowedPackage) {
		return packageName.equals(allowedPackage) || packageName.startsWith(allowedPackage + ".");
	}
}
