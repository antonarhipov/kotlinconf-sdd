package org.example.sdd.importer

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.nio.file.Files

@Component
class ImporterRunner(
    private val properties: ImporterProperties,
    private val directoryScanner: DirectoryScanner = DirectoryScanner(),
    private val summaryPrinter: SummaryPrinter = SummaryPrinter(),
    private val csvFileProcessor: CsvFileProcessor,
    private val fileMover: FileMover = FileMover()
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(ImporterRunner::class.java)

    override fun run(vararg args: String) {
        val out = System.out // Capture PrintStream reference at start per RULE-15

        val inputDir = properties.inputDir
        if (!Files.exists(inputDir)) {
            val errMsg = "Input directory does not exist: $inputDir"
            logger.error(errMsg)
            throw StartupError(errMsg)
        }
        if (!Files.isDirectory(inputDir)) {
            val errMsg = "Input path is not a directory: $inputDir"
            logger.error(errMsg)
            throw StartupError(errMsg)
        }

        // Call DirectoryScanner
        val files = directoryScanner.scan(inputDir)
        if (files.isEmpty()) {
            summaryPrinter.printEmptyTotal(out)
            return
        }

        val processedDir = inputDir.resolve("processed")
        val failedDir = inputDir.resolve("failed")

        var totalFiles = 0
        var totalInserted = 0
        var totalDuplicates = 0
        var totalMalformed = 0
        var totalFailedFiles = 0

        for (file in files) {
            totalFiles++
            val fileName = file.fileName.toString()
            try {
                val result = csvFileProcessor.process(file)
                totalInserted += result.inserted
                totalDuplicates += result.duplicates
                totalMalformed += result.malformed

                summaryPrinter.printFileSummary(out, fileName, result.inserted, result.duplicates, result.malformed)

                val moved = fileMover.move(file, processedDir)
                if (moved == null) {
                    totalFailedFiles++
                }
            } catch (e: Exception) {
                val (reason, ins, mal) = when (e) {
                    is HeaderMissingError -> Triple(e.message ?: "missing header", 0, 0)
                    is HeaderDuplicateError -> Triple(e.message ?: "duplicate header", 0, 0)
                    is FileEncodingError -> Triple(e.message ?: "encoding error", e.inserted, e.malformed)
                    is DatabaseFileError -> Triple(e.message ?: "database error", e.inserted, e.malformed)
                    else -> Triple(e.message ?: "unknown fatal error", 0, 0)
                }

                totalInserted += ins
                totalMalformed += mal

                summaryPrinter.printFileSummary(out, fileName, ins, 0, mal)
                summaryPrinter.printFileFailed(out, reason)

                totalFailedFiles++

                val moved = fileMover.move(file, failedDir)
                if (moved == null) {
                    // Even if filesystem move fails, we already marked as failed_files
                }
            }
        }

        summaryPrinter.printTotal(out, totalFiles, totalInserted, totalDuplicates, totalMalformed, totalFailedFiles)
    }
}
