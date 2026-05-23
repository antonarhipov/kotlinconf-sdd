package org.example.sdd.importer

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
}
