package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.example.sdd.TestcontainersConfiguration
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths

@SpringBootTest(properties = ["importer.input-dir=build/mixed-run-input"])
@Import(TestcontainersConfiguration::class)
class MixedRunIntegrationTest {

    @Autowired
    private lateinit var runner: ImporterRunner

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var runDeduplicator: RunDeduplicator

    private val inputDir = Paths.get("build/mixed-run-input")
    private val outContent = ByteArrayOutputStream()
    private val originalOut = System.out

    companion object {
        @org.junit.jupiter.api.BeforeAll
        @JvmStatic
        fun beforeAll() {
            val path = Paths.get("build/mixed-run-input")
            Files.createDirectories(path)
        }
    }

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM temperature_readings")
        runDeduplicator.clear()

        Files.createDirectories(inputDir)
        Files.deleteIfExists(inputDir.resolve("processed"))
        Files.deleteIfExists(inputDir.resolve("failed"))

        System.setOut(PrintStream(outContent))
    }

    @AfterEach
    fun tearDown() {
        System.setOut(originalOut)
        if (Files.exists(inputDir)) {
            Files.walk(inputDir).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    @Test
    fun `should run mixed files successfully and output total summary line`() {
        val file1Content = """
            name,datetime,temp
            London,2026-05-23T12:00:00,15.50
            Paris,invalid-date,18.20
            Berlin,2026-05-23T12:00:00
        """.trimIndent()

        val file2Content = """
            name,temp
            London,15.50
        """.trimIndent()

        val file1 = inputDir.resolve("01_success_with_malformed.csv")
        val file2 = inputDir.resolve("02_failed_file.csv")

        Files.writeString(file1, file1Content)
        Files.writeString(file2, file2Content)

        // Run the importer normally. It should not throw any exceptions (equivalent to exit code 0)
        runner.run()

        // 1. Verify files are in the right directories
        assertThat(inputDir.resolve("processed/01_success_with_malformed.csv")).exists()
        assertThat(inputDir.resolve("failed/02_failed_file.csv")).exists()

        // 2. Verify printed stdout output
        val output = outContent.toString()
        val lines = output.lineSequence().filter { it.isNotBlank() }.toList()

        assertThat(lines).anySatisfy { line ->
            assertThat(line).contains("01_success_with_malformed.csv: inserted=1, duplicates=0, malformed=2")
        }
        assertThat(lines).anySatisfy { line ->
            assertThat(line).contains("02_failed_file.csv: inserted=0, duplicates=0, malformed=0")
        }
        assertThat(lines).anySatisfy { line ->
            assertThat(line).contains("FAILED: missing required header(s): datetime")
        }
        assertThat(lines).anySatisfy { line ->
            assertThat(line).contains("TOTAL: files=2, inserted=1, duplicates=0, malformed=2, failed_files=1")
        }
    }
}
