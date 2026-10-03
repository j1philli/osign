package osign.shared.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Keeps the documents in `examples/` in sync with the model and validation rules. */
class ExamplesTest {

    private val examplesDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "examples") }
        .firstOrNull { File(it.parentFile, "project.yaml").isFile && it.isDirectory }
        ?: fail("Could not locate the project's examples/ directory")

    @Test
    fun `every example parses and validates without warnings`() {
        val examples = examplesDir.listFiles { file -> file.name.endsWith(".osign.json") }.orEmpty().sorted()
        assertTrue(examples.isNotEmpty(), "No examples found in $examplesDir")

        examples.forEach { file ->
            val result = parseOsign(file.readText())
            val document = assertIs<OsignParseResult.Success>(result, "${file.name}: $result").document
            val warnings = validateOsign(document).warnings
            assertTrue(warnings.isEmpty(), "${file.name} has validation warnings: $warnings")
        }
    }

    @Test
    fun `every local asset referenced by an example exists`() {
        examplesDir.listFiles { file -> file.name.endsWith(".osign.json") }.orEmpty().sorted().forEach { file ->
            val document = (parseOsign(file.readText()) as OsignParseResult.Success).document
            collectAssetSources(document).values
                .filterNot { it.contains("://") || it.startsWith("data:") }
                .forEach { source ->
                    assertTrue(File(file.parentFile, source).isFile, "${file.name} references missing asset '$source'")
                }
        }
    }
}
