package org.example.sdd.importer

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.example.sdd.TestcontainersConfiguration
import java.nio.file.Files
import java.nio.file.Paths

@SpringBootTest(properties = ["importer.input-dir=build/test-input"])
@Import(TestcontainersConfiguration::class)
class CsvDeduplicationIntegrationTest {

    @Autowired
    private lateinit var runner: ImporterRunner

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var runDeduplicator: RunDeduplicator

    private val inputDir = Paths.get("build/test-input")
    private lateinit var listAppender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    companion object {
        @org.junit.jupiter.api.BeforeAll
        @JvmStatic
        fun beforeAll() {
            val path = java.nio.file.Paths.get("build/test-input")
            java.nio.file.Files.createDirectories(path)
        }
    }

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM temperature_readings")
        runDeduplicator.clear()

        Files.createDirectories(inputDir)
        // Clean up processed/failed if they exist
        Files.deleteIfExists(inputDir.resolve("processed"))
        Files.deleteIfExists(inputDir.resolve("failed"))

        logger = LoggerFactory.getLogger(CsvFileProcessor::class.java) as Logger
        listAppender = ListAppender()
        listAppender.start()
        logger.addAppender(listAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(listAppender)
        jdbcTemplate.execute("DELETE FROM temperature_readings")

        // Recursively clean up inputDir
        if (Files.exists(inputDir)) {
            Files.walk(inputDir).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    @Test
    fun `should deduplicate rows across files and DB on end-to-end run`() {
        // Create first CSV file
        val file1Content = """
            name,datetime,temp
            London,2026-05-23T12:00:00,15.50
            Paris,2026-05-23T12:00:00,18.20
            London,2026-05-23T12:00:00,15.50
        """.trimIndent()
        
        // Create second CSV file
        val file2Content = """
            name,datetime,temp
            London,2026-05-23T12:00:00,16.00
            Berlin,2026-05-23T12:00:00,14.30
            london,2026-05-23T12:00:00,15.50
        """.trimIndent()

        val file1 = inputDir.resolve("01_london_paris.csv")
        val file2 = inputDir.resolve("02_london_berlin.csv")

        Files.writeString(file1, file1Content)
        Files.writeString(file2, file2Content)

        // Run the importer!
        runner.run()

        // 1. Verify DB rows: should have only 3 unique rows: London (15.50), Paris (18.20), Berlin (14.30)
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM temperature_readings", Int::class.java)
        assertThat(count).isEqualTo(3)

        // London should have temp of 15.50 (the first inserted value)
        val londonTemp = jdbcTemplate.queryForObject(
            "SELECT temp FROM temperature_readings WHERE name = 'London'",
            java.math.BigDecimal::class.java
        )
        assertThat(londonTemp).isEqualByComparingTo("15.50")

        // 2. Verify duplicate logs:
        // - In file1: London (line 4) is a duplicate of line 2 (in-memory) -> INFO duplicate
        // - In file2: London (line 2) is a duplicate of file1 London (in-memory) -> INFO duplicate
        // - In file2: london (line 4) is a duplicate of file1 London (in-memory / accent-case-insensitive) -> INFO duplicate
        val logs = listAppender.list.filter { it.level.toString() == "INFO" }
        assertThat(logs).isNotEmpty()

        // Verify some duplicate log line matches key=<name>|<datetime> shape
        val duplicateLogs = logs.map { it.formattedMessage }
        assertThat(duplicateLogs).anySatisfy { log ->
            assertThat(log).contains("file=01_london_paris.csv")
            assertThat(log).contains("line=4")
            assertThat(log).contains("key=London|2026-05-23T12:00")
        }
        assertThat(duplicateLogs).anySatisfy { log ->
            assertThat(log).contains("file=02_london_berlin.csv")
            assertThat(log).contains("line=2")
            assertThat(log).contains("key=London|2026-05-23T12:00")
        }
        assertThat(duplicateLogs).anySatisfy { log ->
            assertThat(log).contains("file=02_london_berlin.csv")
            assertThat(log).contains("line=4")
            assertThat(log).contains("key=london|2026-05-23T12:00")
        }
    }
}
