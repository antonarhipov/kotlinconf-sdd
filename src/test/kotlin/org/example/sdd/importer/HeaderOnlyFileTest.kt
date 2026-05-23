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

@SpringBootTest(properties = ["importer.input-dir=build/header-only-input"])
@Import(TestcontainersConfiguration::class)
class HeaderOnlyFileTest {

    @Autowired
    private lateinit var runner: ImporterRunner

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var runDeduplicator: RunDeduplicator

    private val inputDir = Paths.get("build/header-only-input")
    private val outContent = ByteArrayOutputStream()
    private val originalOut = System.out

    companion object {
        @org.junit.jupiter.api.BeforeAll
        @JvmStatic
        fun beforeAll() {
            val path = Paths.get("build/header-only-input")
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
    fun `should process header-only file as success and move to processed`() {
        val fileContent = """
            name,datetime,temp
        """.trimIndent()

        val file = inputDir.resolve("header_only.csv")
        Files.writeString(file, fileContent)

        // Run the importer runner
        runner.run()

        // 1. Verify file was moved to processed/
        assertThat(inputDir.resolve("processed/header_only.csv")).exists()
        assertThat(inputDir.resolve("header_only.csv")).doesNotExist()

        // 2. Verify stdout output
        val output = outContent.toString()
        val lines = output.lineSequence().filter { it.isNotBlank() }.toList()

        assertThat(lines).anySatisfy { line ->
            assertThat(line).contains("header_only.csv: inserted=0, duplicates=0, malformed=0")
        }
        assertThat(lines).anySatisfy { line ->
            assertThat(line).contains("TOTAL: files=1, inserted=0, duplicates=0, malformed=0, failed_files=0")
        }
    }
}
