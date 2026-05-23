package org.example.sdd.importer

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Component
class FileMover {
    private val logger = LoggerFactory.getLogger(FileMover::class.java)

    fun move(file: Path, destinationDir: Path): Path? {
        val fileNameStr = file.fileName.toString()
        val targetPath = destinationDir.resolve(fileNameStr)

        try {
            if (Files.exists(targetPath)) {
                throw FileAlreadyExistsException(targetPath.toString())
            }
            return Files.move(file, targetPath, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: FileAlreadyExistsException) {
            val ext = fileNameStr.substringAfterLast('.', "")
            val base = if (ext.isNotEmpty()) fileNameStr.substringBeforeLast('.') else fileNameStr
            val newName = "$base-${System.currentTimeMillis()}${if (ext.isNotEmpty()) ".$ext" else ""}"
            val retryPath = destinationDir.resolve(newName)
            try {
                if (Files.exists(retryPath)) {
                    throw FileAlreadyExistsException(retryPath.toString())
                }
                return Files.move(file, retryPath, StandardCopyOption.ATOMIC_MOVE)
            } catch (retryEx: IOException) {
                logger.error("Failed to move file after collision retry {}: {}", file, retryEx.message, retryEx)
                return null
            }
        } catch (e: IOException) {
            logger.error("Failed to move file {}: {}", file, e.message, e)
            return null
        }
    }
}
