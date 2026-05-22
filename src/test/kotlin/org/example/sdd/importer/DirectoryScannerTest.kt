package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DirectoryScannerTest {

    @TempDir
    lateinit var tempDir: Path

    private val scanner = DirectoryScanner()

    @Test
    fun `should scan and auto-create processed and failed directories`() {
        val processedDir = tempDir.resolve("processed")
        val failedDir = tempDir.resolve("failed")

        assertThat(processedDir).doesNotExist()
        assertThat(failedDir).doesNotExist()

        val results = scanner.scan(tempDir)

        assertThat(results).isEmpty()
        assertThat(processedDir).exists().isDirectory()
        assertThat(failedDir).exists().isDirectory()
    }

    @Test
    fun `should select eligible csv files and sort them naturally`() {
        // Create files in tempDir
        val fileC = Files.createFile(tempDir.resolve("c.csv"))
        val fileA = Files.createFile(tempDir.resolve("a.CSV"))
        val fileB = Files.createFile(tempDir.resolve("b.Csv"))

        // Create ineligible files
        val txtFile = Files.createFile(tempDir.resolve("d.txt"))
        val dotFile = Files.createFile(tempDir.resolve(".hidden.csv"))
        val tmpFile = Files.createFile(tempDir.resolve("e.csv.tmp"))

        // Create a subdirectory with a csv inside (should be ignored)
        val subDir = Files.createDirectory(tempDir.resolve("subdir"))
        Files.createFile(subDir.resolve("nested.csv"))

        val results = scanner.scan(tempDir)

        // Only c.csv, a.CSV, b.Csv are eligible
        // Natural order: a.CSV -> b.Csv -> c.csv (case-sensitive) or standard alphabetical
        // Let's assert exactly standard alphabetical comparison of names: a.CSV, b.Csv, c.csv
        assertThat(results)
            .hasSize(3)
            .containsExactly(fileA, fileB, fileC)
    }
}
