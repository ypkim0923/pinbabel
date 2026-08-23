import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

data class BackendComplianceChangeSet(
    val id: String,
    val author: String,
    val source: String,
)

fun backendComplianceAttribute(tag: String, name: String): String? =
    Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']+)["']""")
        .find(tag)
        ?.groupValues
        ?.get(1)

fun backendComplianceYamlValue(line: String): String =
    line.substringAfter(':').trim().trim('"', '\'')

fun backendComplianceYamlChangeSets(text: String, source: String): List<BackendComplianceChangeSet> {
    val results = mutableListOf<BackendComplianceChangeSet>()
    var inChangeSet = false
    var id: String? = null
    var author: String? = null

    fun flush() {
        if (inChangeSet) {
            results += BackendComplianceChangeSet(
                id = id ?: "<missing>",
                author = author ?: "<missing>",
                source = source,
            )
        }
        id = null
        author = null
    }

    text.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (Regex("""^-?\s*changeSet\s*:""").containsMatchIn(trimmed)) {
            flush()
            inChangeSet = true
        } else if (inChangeSet && Regex("""^id\s*:""").containsMatchIn(trimmed)) {
            id = backendComplianceYamlValue(trimmed)
        } else if (inChangeSet && Regex("""^author\s*:""").containsMatchIn(trimmed)) {
            author = backendComplianceYamlValue(trimmed)
        }
    }
    flush()
    return results
}

fun backendComplianceChangeSets(file: File): List<BackendComplianceChangeSet> {
    val text = file.readText()
    val source = file.path
    return when (file.extension.lowercase()) {
        "xml" -> Regex("""<changeSet\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { match ->
                BackendComplianceChangeSet(
                    id = backendComplianceAttribute(match.value, "id") ?: "<missing>",
                    author = backendComplianceAttribute(match.value, "author") ?: "<missing>",
                    source = source,
                )
            }
            .toList()
        "yaml", "yml" -> backendComplianceYamlChangeSets(text, source)
        "json" -> Regex(
            """"changeSet"\s*:\s*\{(.*?)\}""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
            .findAll(text)
            .map { match ->
                val body = match.groupValues[1]
                val id = Regex(""""id"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                val author = Regex(""""author"\s*:\s*"([^"]+)"""")
                    .find(body)
                    ?.groupValues
                    ?.get(1)
                BackendComplianceChangeSet(
                    id = id ?: "<missing>",
                    author = author ?: "<missing>",
                    source = source,
                )
            }
            .toList()
        "sql" -> Regex(
            """(?im)^\s*--\s*changeset\s+([^:\s]+):([^\s]+)""",
        )
            .findAll(text)
            .map { match ->
                BackendComplianceChangeSet(
                    id = match.groupValues[2],
                    author = match.groupValues[1],
                    source = source,
                )
            }
            .toList()
        else -> emptyList()
    }
}

if (!plugins.hasPlugin("java")) {
    throw GradleException("backend compliance pack requires the Java plugin")
}

dependencies {
    add(
        "testImplementation",
        platform("org.junit:junit-bom:6.0.3"),
    )
    add("testImplementation", "org.junit.jupiter:junit-jupiter")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    add(
        "testImplementation",
        platform("org.springframework.modulith:spring-modulith-bom:2.1.0"),
    )
    add(
        "testImplementation",
        "org.springframework.modulith:spring-modulith-starter-test",
    )
    add(
        "testImplementation",
        "com.tngtech.archunit:archunit-junit5:1.4.2",
    )
}

val backendComplianceLiquibaseRoot = layout.projectDirectory.dir(
    "src/main/resources/db/changelog"
)

val validateLiquibaseChangeSets = tasks.register("validateLiquibaseChangeSets") {
    group = "verification"
    description = "Validates Liquibase changeSet authors and duplicate identities."
    inputs.files(
        fileTree(backendComplianceLiquibaseRoot) {
            include("**/*.xml", "**/*.yaml", "**/*.yml", "**/*.json", "**/*.sql")
        }
    )

    doLast {
        val files = fileTree(backendComplianceLiquibaseRoot) {
            include("**/*.xml", "**/*.yaml", "**/*.yml", "**/*.json", "**/*.sql")
        }.files.sortedBy { it.path }
        val changeSets = files.flatMap(::backendComplianceChangeSets)
        val malformed = files.filter { file ->
            val text = file.readText()
            val declaresChangeSet = when (file.extension.lowercase()) {
                "xml" -> Regex("""<changeSet\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
                "yaml", "yml" -> Regex("""(?m)^\s*-?\s*changeSet\s*:""")
                    .containsMatchIn(text)
                "json" -> Regex(""""changeSet"\s*:""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(text)
                "sql" -> Regex("""(?im)^\s*--\s*changeset\s+""").containsMatchIn(text)
                else -> false
            }
            declaresChangeSet && backendComplianceChangeSets(file).isEmpty()
        }
        if (malformed.isNotEmpty()) {
            throw GradleException(
                "Could not extract id and author from Liquibase changeSets:\n"
                    + malformed.joinToString("\n") { "- ${it.path}" }
            )
        }

        val incomplete = changeSets.filter {
            it.id == "<missing>" || it.author == "<missing>"
        }
        if (incomplete.isNotEmpty()) {
            throw GradleException(
                "Every Liquibase changeSet must declare id and author:\n"
                    + incomplete.joinToString("\n") {
                        "- ${it.source}: ${it.author}:${it.id}"
                    }
            )
        }

        val invalidAuthors = changeSets.filter { it.author != "ypkim" }
        if (invalidAuthors.isNotEmpty()) {
            throw GradleException(
                "Every Liquibase changeSet author must be ypkim:\n"
                    + invalidAuthors.joinToString("\n") {
                        "- ${it.source}: ${it.author}:${it.id}"
                    }
            )
        }

        val duplicates = changeSets
            .groupBy { "${it.author}:${it.id}" }
            .filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            throw GradleException(
                "Duplicate Liquibase changeSet identities:\n"
                    + duplicates.entries.joinToString("\n") { (identity, entries) ->
                        "- $identity: ${entries.joinToString { it.source }}"
                    }
            )
        }
    }
}

val validateInternalCodeRegistry = tasks.register<Exec>("validateInternalCodeRegistry") {
    group = "verification"
    description = "Runs exhaustive project-wide Internal Code validation."
    dependsOn(tasks.named("generateInternalCodeInventory"))
    val validator = layout.projectDirectory.file(
        "gradle/backend-compliance/validate_internal_code_registry.py"
    )
    val registry = rootProject.layout.projectDirectory.file("config/internal-code/registry.json")
    val declarations = rootProject.layout.projectDirectory.file("build/internal-code/declarations.json")
    val occurrences = rootProject.layout.projectDirectory.file("build/internal-code/occurrences.json")
    inputs.files(validator, registry, declarations, occurrences)
    commandLine(
        "python3",
        validator.asFile.absolutePath,
        registry.asFile.absolutePath,
        "--root",
        rootProject.layout.projectDirectory.asFile.absolutePath,
        "--declarations",
        declarations.asFile.absolutePath,
        "--occurrences",
        occurrences.asFile.absolutePath,
    )
}

val backendComplianceSourceSets = extensions.getByType<SourceSetContainer>()

tasks.named<Test>("test") {
    exclude("backend/compliance/**")
}

val backendArchitectureTest = tasks.register<Test>("backendArchitectureTest") {
    group = "verification"
    description = "Runs Spring Modulith and ArchUnit backend compliance tests."
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = backendComplianceSourceSets.named("test").get().output.classesDirs
    classpath = backendComplianceSourceSets.named("test").get().runtimeClasspath
    include("backend/compliance/**")
    useJUnitPlatform()
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") {
    dependsOn(
        backendArchitectureTest,
        validateLiquibaseChangeSets,
        validateInternalCodeRegistry,
    )
}
