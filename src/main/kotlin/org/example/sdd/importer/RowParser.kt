package org.example.sdd.importer

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField

sealed class RowResult {
    data class Valid(
        val name: String,
        val datetime: LocalDateTime,
        val temp: BigDecimal
    ) : RowResult()

    data class Malformed(
        val reason: String
    ) : RowResult()
}

object RowParser {
    private val formatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd")
        .appendPattern("['T'][ ]")
        .appendPattern("HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .toFormatter()

    fun parseRow(name: String, datetime: String, temp: String): RowResult {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return RowResult.Malformed("empty name")
        }
        if (trimmedName.length > 255) {
            return RowResult.Malformed("name too long")
        }

        val trimmedDatetime = datetime.trim()
        if (trimmedDatetime.isEmpty()) {
            return RowResult.Malformed("empty datetime")
        }

        val trimmedTemp = temp.trim()
        if (trimmedTemp.isEmpty()) {
            return RowResult.Malformed("empty temp")
        }

        val parsedDatetime = try {
            LocalDateTime.parse(trimmedDatetime, formatter)
        } catch (e: DateTimeParseException) {
            return RowResult.Malformed("unparseable datetime")
        }

        val parsedTemp = try {
            BigDecimal(trimmedTemp)
        } catch (e: NumberFormatException) {
            return RowResult.Malformed("non-numeric temp")
        }

        // Validate DECIMAL(5,2) constraints
        val scale = parsedTemp.scale()
        val precision = parsedTemp.precision()
        if (precision - scale > 3 || scale > 2 || parsedTemp.abs() > BigDecimal("999.99")) {
            return RowResult.Malformed("temp out of range")
        }

        val scaledTemp = parsedTemp.setScale(2, RoundingMode.UNNECESSARY)

        return RowResult.Valid(trimmedName, parsedDatetime, scaledTemp)
    }
}
