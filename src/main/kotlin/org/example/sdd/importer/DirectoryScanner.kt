package org.example.sdd.importer

import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

@Component
class DirectoryScanner {

    fun scan(inputDir: Path): List<Path> {
        // Ensure processed/ and failed/ exist under inputDir
        val processedDir = inputDir.resolve("processed")
        val failedDir = inputDir.resolve("failed")
        Files.createDirectories(processedDir)
        Files.createDirectories(failedDir)

        // List files in inputDir and filter
        Files.list(inputDir).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) }
                .filter { path ->
                    val filename = path.fileName.toString()
                    val lowercaseName = filename.lowercase()
                    lowercaseName.endsWith(".csv") &&
                            !filename.startsWith(".") &&
                            !lowercaseName.endsWith(".tmp")
                }
                .sorted(compareBy { it.fileName.toString() })
                .collect(Collectors.toList())
        }
    }
}
