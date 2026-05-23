package org.example.sdd.importer

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

class RowLoggingTest {

    @TempDir
    lateinit var tempDir: Path

    private val processor = CsvFileProcessor()
    private lateinit var listAppender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    @BeforeEach
    fun setUp() {
        logger = LoggerFactory.getLogger(CsvFileProcessor::class.java) as Logger
        listAppender = ListAppender()
        listAppender.start()
        logger.addAppender(listAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(listAppender)
    }

    @Test
    fun `should log malformed rows at WARN level with exact shape`() {
        val csvContent = """
            name,datetime,temp
            ,2026-05-23T09:15:30,23.45
            Station A,,23.45
            Station B,2026-05-23T09:15:30,
            Station C,2026-05-23 09-15-30,23.45
            Station D,2026-05-23T09:15:30,invalid
            Station E,2026-05-23T09:15:30,1000.00
            Station F,2026-05-23T09:15:30,23.45,extra
        """.trimIndent()

        val file = tempDir.resolve("malformed.csv")
        Files.writeString(file, csvContent)

        processor.process(file)

        val logs = listAppender.list
        assertThat(logs).hasSize(7)

        assertThat(logs[0].level.toString()).isEqualTo("WARN")
        assertThat(logs[0].formattedMessage).isEqualTo("file=malformed.csv line=2 reason=empty name")

        assertThat(logs[1].level.toString()).isEqualTo("WARN")
        assertThat(logs[1].formattedMessage).isEqualTo("file=malformed.csv line=3 reason=empty datetime")

        assertThat(logs[2].level.toString()).isEqualTo("WARN")
        assertThat(logs[2].formattedMessage).isEqualTo("file=malformed.csv line=4 reason=empty temp")

        assertThat(logs[3].level.toString()).isEqualTo("WARN")
        assertThat(logs[3].formattedMessage).isEqualTo("file=malformed.csv line=5 reason=unparseable datetime")

        assertThat(logs[4].level.toString()).isEqualTo("WARN")
        assertThat(logs[4].formattedMessage).isEqualTo("file=malformed.csv line=6 reason=non-numeric temp")

        assertThat(logs[5].level.toString()).isEqualTo("WARN")
        assertThat(logs[5].formattedMessage).isEqualTo("file=malformed.csv line=7 reason=temp out of range")

        assertThat(logs[6].level.toString()).isEqualTo("WARN")
        assertThat(logs[6].formattedMessage).isEqualTo("file=malformed.csv line=8 reason=field count mismatch")
    }
}
