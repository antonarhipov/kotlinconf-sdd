package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.example.sdd.TestcontainersConfiguration
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths

@SpringBootTest(properties = ["importer.input-dir=build/db-error-input"])
@Import(TestcontainersConfiguration::class)
class DatabaseErrorIntegrationTest {

    @Autowired
    private lateinit var runner: ImporterRunner

    @MockitoBean
    private lateinit var repository: TemperatureReadingRepository

    @Autowired
    private lateinit var runDeduplicator: RunDeduplicator

    private val inputDir = Paths.get("build/db-error-input")
    private val outContent = ByteArrayOutputStream()
    private val originalOut = System.out

    companion object {
        @org.junit.jupiter.api.BeforeAll
        @JvmStatic
        fun beforeAll() {
            val path = java.nio.file.Paths.get("build/db-error-input")
            java.nio.file.Files.createDirectories(path)
        }
    }

    @BeforeEach
    fun setUp() {
        runDeduplicator.clear()
        Files.createDirectories(inputDir)
        Files.deleteIfExists(inputDir.resolve("processed"))
        Files.deleteIfExists(inputDir.resolve("failed"))

        // Redirect System.out to capture printed summary
        System.setOut(PrintStream(outContent))

        // Stub repository to throw database exception on any insert using Kotlin-safe matchers
        org.mockito.Mockito.doThrow(org.springframework.dao.DataRetrievalFailureException("simulated db failure"))
            .`when`(repository).insertIfAbsent(
                org.mockito.Mockito.anyString() ?: "",
                org.mockito.Mockito.any() ?: java.time.LocalDateTime.now(),
                org.mockito.Mockito.any() ?: java.math.BigDecimal.ZERO
            )
    }

    @AfterEach
    fun tearDown() {
        System.setOut(originalOut)
        // Clean up inputDir
        if (Files.exists(inputDir)) {
            Files.walk(inputDir).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    @Test
    fun `should stop processing current file and move it to failed on non-duplicate database error`() {
        val csvContent = """
            name,datetime,temp
            London,2026-05-23T12:00:00,15.50
        """.trimIndent()

        val file = inputDir.resolve("db_fail.csv")
        Files.writeString(file, csvContent)

        // Run the runner!
        runner.run()

        // 1. Verify file was moved to failed/
        val failedFile = inputDir.resolve("failed/db_fail.csv")
        assertThat(Files.exists(failedFile)).isTrue()

        // 2. Verify stdout output
        val output = outContent.toString()
        assertThat(output).contains("db_fail.csv: inserted=0, duplicates=0, malformed=0")
        assertThat(output).contains("FAILED: database error")
    }
}
