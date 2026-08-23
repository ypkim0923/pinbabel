plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ypkim"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.1.0"
extra["embabelVersion"] = "1.5.0"
extra["jMoleculesVersion"] = "1.10.0"
extra["jMoleculesIntegrationsVersion"] = "0.33.0"

dependencies {
	implementation("com.embabel.agent:embabel-agent-starter-byok:${property("embabelVersion")}")
	implementation("com.embabel.agent:embabel-agent-starter-shell:${property("embabelVersion")}")
	implementation("org.jmolecules:jmolecules-ddd:${property("jMoleculesVersion")}")
	implementation("org.jmolecules:jmolecules-hexagonal-architecture:${property("jMoleculesVersion")}")
	implementation("org.springframework.boot:spring-boot-h2console")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-liquibase")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-starter-jpa")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("com.h2database:h2")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("com.embabel.agent:embabel-agent-test:${property("embabelVersion")}")
	testImplementation("org.jmolecules.integrations:jmolecules-archunit:${property("jMoleculesIntegrationsVersion")}")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

apply(from = "gradle/internal-code-inventory.gradle.kts")

// BEGIN BACKEND ENGINEERING COMPLIANCE
apply(from = "gradle/backend-compliance.gradle.kts")
// END BACKEND ENGINEERING COMPLIANCE
