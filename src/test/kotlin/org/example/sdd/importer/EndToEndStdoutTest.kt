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

@SpringBootTest(properties = ["importer.input-dir=build/e2e-stdout-input"])
@Import(TestcontainersConfiguration::class)
class EndToEndStdoutTest {

    @Autowired
    private lateinit var runner: ImporterRunner

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var runDeduplicator: RunDeduplicator

    private val inputDir = Paths.get("build/e2e-stdout-input")
    private val outContent = ByteArrayOutputStream()
    private val originalOut = System.out

    companion object {
        @org.junit.jupiter.api.BeforeAll
        @JvmStatic
        fun beforeAll() {
            val path = Paths.get("build/e2e-stdout-input")
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
    fun `should match exact multi-line summary transcript`() {
        val file1Content = """
            name,datetime,temp
            London,2026-05-23T12:00:00,15.50
        """.trimIndent()

        val file2Content = """
            name,datetime,temp
            Paris,2026-05-23T12:00:00,18.20
            Paris,invalid-date,18.20
        """.trimIndent()

        val file3Content = """
            name,temp
            London,15.50
        """.trimIndent()

        val file1 = inputDir.resolve("01_healthy.csv")
        val file2 = inputDir.resolve("02_some_malformed.csv")
        val file3 = inputDir.resolve("03_failing.csv")

        Files.writeString(file1, file1Content)
        Files.writeString(file2, file2Content)
        Files.writeString(file3, file3Content)

        // Invoke the runner
        runner.run()

        // Capture printed output
        val output = outContent.toString()
        val lines = output.lineSequence()
            .filter { it.isNotBlank() }
            .filter { line ->
                line.contains(": inserted=") || line.startsWith("FAILED:") || line.startsWith("TOTAL:")
            }
            .toList()

        // Assert exact line count and format
        assertThat(lines).hasSize(5)
        assertThat(lines[0]).isEqualTo("01_healthy.csv: inserted=1, duplicates=0, malformed=0")
        assertThat(lines[1]).isEqualTo("02_some_malformed.csv: inserted=1, duplicates=0, malformed=1")
        assertThat(lines[2]).isEqualTo("03_failing.csv: inserted=0, duplicates=0, malformed=0")
        assertThat(lines[3]).isEqualTo("FAILED: missing required header(s): datetime")
        assertThat(lines[4]).isEqualTo("TOTAL: files=3, inserted=2, duplicates=0, malformed=1, failed_files=1")
    }
}
