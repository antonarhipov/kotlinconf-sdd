package org.example.sdd.importer

import java.nio.file.Path

class CsvFileProcessor {
    fun process(file: Path): CsvProcessResult {
        return CsvProcessResult(0, 0)
    }
}

data class CsvProcessResult(
    val inserted: Int,
    val malformed: Int
)

class HeaderMissingError(message: String) : RuntimeException(message)
class HeaderDuplicateError(message: String) : RuntimeException(message)
class FileEncodingError(message: String) : RuntimeException(message)
