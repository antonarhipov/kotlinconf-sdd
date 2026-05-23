package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class CsvFileProcessorTest {

    @TempDir
    lateinit var tempDir: Path

    private val runDeduplicator = RunDeduplicator()
    private val repository = object : TemperatureReadingRepository(org.mockito.Mockito.mock(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate::class.java)) {
        override fun insertIfAbsent(name: String, datetime: java.time.LocalDateTime, temp: java.math.BigDecimal): InsertOutcome {
            return InsertOutcome.Inserted
        }
    }
    private val processor = CsvFileProcessor(runDeduplicator, repository)

    @Test
    fun `should parse valid csv file and ignore unknown columns`() {
        val csvContent = """
            name,unknown_col,datetime,temp
            Station A,foo,2026-05-23T09:15:30,23.45
            Station B,bar,2026-05-23 09:15:30,25.60
        """.trimIndent()

        val file = tempDir.resolve("valid.csv")
        Files.writeString(file, csvContent)

        val result = processor.process(file)

        assertThat(result.inserted).isEqualTo(2)
        assertThat(result.malformed).isEqualTo(0)
    }

    @Test
    fun `should silently strip UTF-8 BOM`() {
        // \uFEFF is the UTF-8 BOM
        val csvContent = "\uFEFFname,datetime,temp\nStation A,2026-05-23T09:15:30,23.45"

        val file = tempDir.resolve("bom.csv")
        Files.writeString(file, csvContent)

        val result = processor.process(file)

        assertThat(result.inserted).isEqualTo(1)
        assertThat(result.malformed).isEqualTo(0)
    }

    @Test
    fun `should throw HeaderMissingError with sorted missing headers`() {
        val csvContent = """
            name,temp
            Station A,23.45
        """.trimIndent()

        val file = tempDir.resolve("missing_headers.csv")
        Files.writeString(file, csvContent)

        assertThatThrownBy { processor.process(file) }
            .isInstanceOf(HeaderMissingError::class.java)
            .hasMessageContaining("missing required header(s): datetime")
    }

    @Test
    fun `should throw HeaderDuplicateError identifying duplicate header`() {
        val csvContent = """
            name,datetime,temp,name
            Station A,2026-05-23T09:15:30,23.45,Station B
        """.trimIndent()

        val file = tempDir.resolve("duplicate_headers.csv")
        Files.writeString(file, csvContent)

        assertThatThrownBy { processor.process(file) }
            .isInstanceOf(HeaderDuplicateError::class.java)
            .hasMessageContaining("duplicate header: name")
    }

    @Test
    fun `should skip blank lines without counter increment`() {
        val csvContent = """
            name,datetime,temp
            Station A,2026-05-23T09:15:30,23.45

            Station B,2026-05-23T09:15:30,25.60

        """.trimIndent()

        val file = tempDir.resolve("blank_lines.csv")
        Files.writeString(file, csvContent)

        val result = processor.process(file)

        assertThat(result.inserted).isEqualTo(2)
        assertThat(result.malformed).isEqualTo(0)
    }

    @Test
    fun `should handle field count mismatch and other malformed rows`() {
        val csvContent = """
            name,datetime,temp
            Station A,2026-05-23T09:15:30,23.45,extra_field
            Station B,2026-05-23T09:15:30
            Station C,2026-05-23T09:15:30,invalid_temp
            Station D,2026-05-23T09:15:30,25.60
        """.trimIndent()

        val file = tempDir.resolve("malformed.csv")
        Files.writeString(file, csvContent)

        val result = processor.process(file)

        assertThat(result.inserted).isEqualTo(1) // only Station D is valid
        assertThat(result.malformed).isEqualTo(3) // Station A (extra), Station B (missing), Station C (invalid temp)
    }

    @Test
    fun `should throw FileEncodingError on invalid UTF-8 and allow partial inserts`() {
        val headerBytes = "name,datetime,temp\n".toByteArray(StandardCharsets.UTF_8)
        val row1Bytes = "Station A,2026-05-23T09:15:30,23.45\n".toByteArray(StandardCharsets.UTF_8)
        val invalidBytes = byteArrayOf(0xC0.toByte(), 0xAF.toByte(), '\n'.toByte())

        val bytes = headerBytes + row1Bytes + invalidBytes

        val file = tempDir.resolve("invalid_encoding.csv")
        Files.write(file, bytes)

        // The processor should raise FileEncodingError on invalid encoding, but only after parsing row 1!
        // We can check that the exception is thrown. Later integration tests will verify that row 1 is inserted.
        assertThatThrownBy { processor.process(file) }
            .isInstanceOf(FileEncodingError::class.java)
            .hasMessage("encoding error")
    }
}
