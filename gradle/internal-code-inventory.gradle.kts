import com.sun.source.tree.ClassTree
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.MemberSelectTree
import com.sun.source.tree.NewClassTree
import com.sun.source.tree.VariableTree
import com.sun.source.util.JavacTask
import com.sun.source.util.TreePathScanner
import com.sun.source.util.Trees
import java.nio.charset.StandardCharsets
import javax.tools.ToolProvider
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

data class InternalCodeLocation(
	val symbol: String,
	val path: String,
	val line: Long,
	val kind: String? = null,
)

abstract class GenerateInternalCodeInventory : DefaultTask() {

	@get:InputDirectory
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val sourceDirectory: DirectoryProperty

	@get:OutputFile
	abstract val declarationsFile: RegularFileProperty

	@get:OutputFile
	abstract val occurrencesFile: RegularFileProperty

	@TaskAction
	fun generate() {
		val compiler = ToolProvider.getSystemJavaCompiler()
			?: throw GradleException("A JDK is required to generate Internal Code inventories")
		val sourceRoot = sourceDirectory.get().asFile.toPath()
		val sourceFiles = sourceDirectory.asFileTree
			.matching { include("**/*.java") }
			.files
			.sortedBy { it.path }

		val declarations = mutableListOf<InternalCodeLocation>()
		val occurrences = mutableListOf<InternalCodeLocation>()
		compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8).use { fileManager ->
			val units = fileManager.getJavaFileObjectsFromFiles(sourceFiles)
			val task = compiler.getTask(
				null,
				fileManager,
				null,
				listOf("-proc:none"),
				null,
				units,
			) as JavacTask
			val trees = Trees.instance(task)
			task.parse().forEach { unit ->
				val relativePath = sourceRoot.relativize(java.nio.file.Path.of(unit.sourceFile.toUri()))
					.toString()
					.replace(java.io.File.separatorChar, '/')
				val projectPath = "src/main/java/$relativePath"
				InventoryScanner(unit, trees, projectPath, declarations, occurrences).scan(unit, null)
			}
		}

		writeInventory(declarationsFile.get().asFile, "declarations", declarations)
		writeInventory(occurrencesFile.get().asFile, "occurrences", occurrences)
	}

	private fun writeInventory(file: File, key: String, locations: List<InternalCodeLocation>) {
		file.parentFile.mkdirs()
		val sorted = locations.sortedWith(compareBy(InternalCodeLocation::symbol, InternalCodeLocation::path, InternalCodeLocation::line))
		val items = sorted.joinToString(",\n") { location ->
			val kind = location.kind?.let { "\n      \"kind\": \"${json(it)}\"," } ?: ""
			"""
    {$kind
      "symbol": "${json(location.symbol)}",
      "path": "${json(location.path)}",
      "line": ${location.line}
    }""".trimEnd()
		}
		file.writeText("{\n  \"version\": 1,\n  \"$key\": [$items\n  ]\n}\n")
	}

	private fun json(value: String): String = value
		.replace("\\", "\\\\")
		.replace("\"", "\\\"")
}

class InventoryScanner(
	private val unit: CompilationUnitTree,
	private val trees: Trees,
	private val projectPath: String,
	private val declarations: MutableList<InternalCodeLocation>,
	private val occurrences: MutableList<InternalCodeLocation>,
) : TreePathScanner<Unit, Unit?>() {

	private var internalCodeType: String? = null

	override fun visitClass(node: ClassTree, unused: Unit?) {
		val previous = internalCodeType
		if (node.simpleName.toString().endsWith("InternalCode")) {
			internalCodeType = node.simpleName.toString()
		}
		super.visitClass(node, unused)
		internalCodeType = previous
	}

	override fun visitVariable(node: VariableTree, unused: Unit?) {
		val type = internalCodeType
		val isEnumConstant = node.initializer is NewClassTree && node.type?.toString() == type
		if (type != null && isEnumConstant) {
			declarations += InternalCodeLocation(
				symbol = "$type.${node.name}",
				path = projectPath,
				line = lineOf(node),
			)
		}
		super.visitVariable(node, unused)
	}

	override fun visitNewClass(node: NewClassTree, unused: Unit?) {
		node.arguments
			.filterIsInstance<MemberSelectTree>()
			.firstOrNull { it.expression.toString().endsWith("InternalCode") }
			?.let { code ->
				occurrences += InternalCodeLocation(
					symbol = "${code.expression}.${code.identifier}",
					path = projectPath,
					line = lineOf(code),
					kind = occurrenceKind(projectPath),
				)
			}
		super.visitNewClass(node, unused)
	}

	private fun occurrenceKind(path: String): String = when {
		"/domain/" in path -> "validation-throw"
		"/adapter/" in path -> "external-operation"
		else -> "business-throw"
	}

	private fun lineOf(tree: com.sun.source.tree.Tree): Long {
		val position = trees.sourcePositions.getStartPosition(unit, tree)
		return unit.lineMap.getLineNumber(position)
	}
}

tasks.register<GenerateInternalCodeInventory>("generateInternalCodeInventory") {
	group = "verification"
	description = "Generates exhaustive Internal Code inventories from the Java syntax tree."
	sourceDirectory.set(layout.projectDirectory.dir("src/main/java"))
	declarationsFile.set(layout.buildDirectory.file("internal-code/declarations.json"))
	occurrencesFile.set(layout.buildDirectory.file("internal-code/occurrences.json"))
}
