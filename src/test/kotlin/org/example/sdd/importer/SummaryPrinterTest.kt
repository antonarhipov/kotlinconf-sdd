package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class SummaryPrinterTest {

    private val summaryPrinter = SummaryPrinter()

    @Test
    fun `should print file summary in exact format`() {
        val baos = ByteArrayOutputStream()
        val out = PrintStream(baos)

        summaryPrinter.printFileSummary(out, "data.csv", 12, 3, 1)

        val output = baos.toString().trim()
        assertThat(output).isEqualTo("data.csv: inserted=12, duplicates=3, malformed=1")
    }

    @Test
    fun `should print failed file in exact format`() {
        val baos = ByteArrayOutputStream()
        val out = PrintStream(baos)

        summaryPrinter.printFileFailed(out, "missing required header(s): name")

        val output = baos.toString().trim()
        assertThat(output).isEqualTo("FAILED: missing required header(s): name")
    }

    @Test
    fun `should print empty total in exact format`() {
        val baos = ByteArrayOutputStream()
        val out = PrintStream(baos)

        summaryPrinter.printEmptyTotal(out)

        val output = baos.toString().trim()
        assertThat(output).isEqualTo("TOTAL: files=0, inserted=0, duplicates=0, malformed=0, failed_files=0")
    }

    @Test
    fun `should print total in exact format`() {
        val baos = ByteArrayOutputStream()
        val out = PrintStream(baos)

        summaryPrinter.printTotal(out, 5, 20, 4, 2, 1)

        val output = baos.toString().trim()
        assertThat(output).isEqualTo("TOTAL: files=5, inserted=20, duplicates=4, malformed=2, failed_files=1")
    }
}
