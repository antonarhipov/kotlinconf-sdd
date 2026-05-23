package org.example.sdd.importer

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.nio.file.Files

@Component
class ImporterRunner(
    private val properties: ImporterProperties,
    private val directoryScanner: DirectoryScanner = DirectoryScanner(),
    private val summaryPrinter: SummaryPrinter = SummaryPrinter()
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
        } else {
            // In future phases, we will process files. For now, let's just print the empty total
            summaryPrinter.printEmptyTotal(out)
        }
    }
}
