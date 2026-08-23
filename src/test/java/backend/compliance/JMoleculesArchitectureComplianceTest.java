package backend.compliance;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesArchitectureRules.VerificationDepth;
import org.jmolecules.archunit.JMoleculesDddRules;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@AnalyzeClasses(
	packages = "com.ypkim.pinbabel",
	importOptions = ImportOption.DoNotIncludeTests.class
)
class JMoleculesArchitectureComplianceTest {

	@ArchTest
	static final ArchRule DDD_RULES = JMoleculesDddRules.all();

	@ArchTest
	static final ArchRule HEXAGONAL_ARCHITECTURE =
		JMoleculesArchitectureRules.ensureHexagonal(VerificationDepth.LENIENT);

	@ArchTest
	static final ArchRule INBOUND_PORTS_DECLARE_THEIR_ROLE = classes()
		.that().areInterfaces()
		.and().resideInAPackage("..application.port.in")
		.should().beAnnotatedWith(PrimaryPort.class);

	@ArchTest
	static final ArchRule OUTBOUND_PORTS_DECLARE_THEIR_ROLE = classes()
		.that().areInterfaces()
		.and().resideInAPackage("..application.port.out")
		.should().beAnnotatedWith(SecondaryPort.class);

	@ArchTest
	static final ArchRule INBOUND_ADAPTERS_DECLARE_THEIR_ROLE = classes()
		.that().areTopLevelClasses()
		.and().resideInAPackage("..adapter.in..")
		.should().beAnnotatedWith(PrimaryAdapter.class);

	@ArchTest
	static final ArchRule OUTBOUND_ADAPTERS_DECLARE_THEIR_ROLE = classes()
		.that().areTopLevelClasses()
		.and().resideInAPackage("..adapter.out..")
		.should().beAnnotatedWith(SecondaryAdapter.class);
}
