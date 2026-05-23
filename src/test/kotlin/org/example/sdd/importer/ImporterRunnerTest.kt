package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ImporterRunnerTest {

    @TempDir
    lateinit var tempDir: Path

    private val csvFileProcessor = object : CsvFileProcessor(RunDeduplicator(), org.mockito.Mockito.mock(TemperatureReadingRepository::class.java)) {
        override fun process(file: Path): CsvProcessResult {
            return CsvProcessResult(0, 0, 0)
        }
    }

    @Test
    fun `should throw StartupError when input directory does not exist`() {
        val nonExistentPath = tempDir.resolve("does-not-exist")
        val properties = ImporterProperties(inputDir = nonExistentPath)
        val runner = ImporterRunner(properties, csvFileProcessor = csvFileProcessor)

        assertThatThrownBy { runner.run() }
            .isInstanceOf(StartupError::class.java)
            .hasMessageContaining("Input directory does not exist")
    }

    @Test
    fun `should throw StartupError when input path is a file instead of a directory`() {
        val filePath = tempDir.resolve("regular-file.txt")
        Files.createFile(filePath)
        val properties = ImporterProperties(inputDir = filePath)
        val runner = ImporterRunner(properties, csvFileProcessor = csvFileProcessor)

        assertThatThrownBy { runner.run() }
            .isInstanceOf(StartupError::class.java)
            .hasMessageContaining("Input path is not a directory")
    }

    @Test
    fun `should proceed normally when input path is an existing directory`() {
        val properties = ImporterProperties(inputDir = tempDir)
        val runner = ImporterRunner(properties, csvFileProcessor = csvFileProcessor)

        // Should not throw any exception
        runner.run()
    }

    @Test
    fun `should process multiple files in ascending order and print them followed by total`() {
        // Create files c.csv, a.csv, b.csv in tempDir
        Files.createFile(tempDir.resolve("c.csv"))
        Files.createFile(tempDir.resolve("a.csv"))
        Files.createFile(tempDir.resolve("b.csv"))

        val properties = ImporterProperties(inputDir = tempDir)

        val originalOut = System.out
        val baos = java.io.ByteArrayOutputStream()
        val customOut = java.io.PrintStream(baos)
        System.setOut(customOut)
        try {
            val runner = ImporterRunner(properties, csvFileProcessor = csvFileProcessor)
            runner.run()
        } finally {
            System.setOut(originalOut)
        }

        val lines = baos.toString().lineSequence().filter { it.isNotBlank() }.toList()
        assertThat(lines).hasSize(4)
        assertThat(lines[0]).isEqualTo("a.csv: inserted=0, duplicates=0, malformed=0")
        assertThat(lines[1]).isEqualTo("b.csv: inserted=0, duplicates=0, malformed=0")
        assertThat(lines[2]).isEqualTo("c.csv: inserted=0, duplicates=0, malformed=0")
        assertThat(lines[3]).isEqualTo("TOTAL: files=3, inserted=0, duplicates=0, malformed=0, failed_files=0")
    }
}
