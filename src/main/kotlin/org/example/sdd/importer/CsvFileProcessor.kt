package org.example.sdd.importer

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.DuplicateHeaderMode
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@Component
class CsvFileProcessor {
    private val logger = org.slf4j.LoggerFactory.getLogger(CsvFileProcessor::class.java)

    fun process(file: Path): CsvProcessResult {
        val bufferedReader = try {
            Files.newBufferedReader(file, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            if (isEncodingError(e)) throw FileEncodingError("encoding error")
            throw e
        }

        try {
            bufferedReader.use { reader ->
                // Strip UTF-8 BOM silently if present
                reader.mark(1)
                val firstChar = try {
                    reader.read()
                } catch (e: Exception) {
                    if (isEncodingError(e)) throw FileEncodingError("encoding error")
                    throw e
                }
                if (firstChar != 0xFEFF) {
                    reader.reset()
                }

                val csvFormat = CSVFormat.Builder.create(CSVFormat.RFC4180)
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                    .build()

                val parser = try {
                    CSVParser.parse(reader, csvFormat)
                } catch (e: IllegalArgumentException) {
                    val msg = e.message ?: ""
                    val lowercaseMsg = msg.lowercase(java.util.Locale.ROOT)
                    if (lowercaseMsg.contains("duplicate header name:") || lowercaseMsg.contains("duplicate name:")) {
                        val dupHeader = if (msg.contains("\"")) {
                            msg.substringAfter("\"").substringBefore("\"")
                        } else if (msg.contains("duplicate name: ")) {
                            msg.substringAfter("duplicate name: ").trim().substringBefore(" ")
                        } else {
                            msg.substringAfter("duplicate header name: ").trim().substringBefore(" ")
                        }
                        throw HeaderDuplicateError("duplicate header: $dupHeader")
                    }
                    throw e
                } catch (e: Exception) {
                    if (isEncodingError(e)) throw FileEncodingError("encoding error")
                    throw e
                }

                val headers = parser.headerNames
                val nameHeader = headers.find { it.equals("name", ignoreCase = true) }
                val datetimeHeader = headers.find { it.equals("datetime", ignoreCase = true) }
                val tempHeader = headers.find { it.equals("temp", ignoreCase = true) }

                val missing = mutableListOf<String>()
                if (nameHeader == null) missing.add("name")
                if (datetimeHeader == null) missing.add("datetime")
                if (tempHeader == null) missing.add("temp")

                if (missing.isNotEmpty()) {
                    missing.sort()
                    throw HeaderMissingError("missing required header(s): ${missing.joinToString(", ")}")
                }

                var inserted = 0
                var malformedCount = 0

                val iterator = parser.iterator()
                while (true) {
                    val hasNext = try {
                        iterator.hasNext()
                    } catch (e: Exception) {
                        if (isEncodingError(e)) throw FileEncodingError("encoding error", inserted, malformedCount)
                        throw e
                    }
                    if (!hasNext) break

                    val record = try {
                        iterator.next()
                    } catch (e: Exception) {
                        if (isEncodingError(e)) throw FileEncodingError("encoding error", inserted, malformedCount)
                        throw e
                    }

                    try {
                        val lineNum = parser.currentLineNumber
                        if (record.size() != headers.size) {
                            logger.warn("file={} line={} reason=field count mismatch", file.fileName.toString(), lineNum)
                            malformedCount++
                            continue
                        }

                        val nameVal = record.get(nameHeader)
                        val datetimeVal = record.get(datetimeHeader)
                        val tempVal = record.get(tempHeader)

                        when (val rowResult = RowParser.parseRow(nameVal, datetimeVal, tempVal)) {
                            is RowResult.Valid -> {
                                inserted++
                            }
                            is RowResult.Malformed -> {
                                logger.warn("file={} line={} reason={}", file.fileName.toString(), lineNum, rowResult.reason)
                                malformedCount++
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("file={} line={} reason=row error: {}", file.fileName.toString(), parser.currentLineNumber, e.message)
                        malformedCount++
                    }
                }

                return CsvProcessResult(inserted, malformedCount)
            }
        } catch (e: Exception) {
            if (isEncodingError(e)) throw FileEncodingError("encoding error")
            throw e
        }
    }

    private fun isEncodingError(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is CharacterCodingException) return true
            cause = cause.cause
        }
        return false
    }
}

data class CsvProcessResult(
    val inserted: Int,
    val malformed: Int
)

class HeaderMissingError(message: String) : RuntimeException(message)
class HeaderDuplicateError(message: String) : RuntimeException(message)
class FileEncodingError(message: String, val inserted: Int = 0, val malformed: Int = 0) : RuntimeException(message)
