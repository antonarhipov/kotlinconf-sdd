package org.example.sdd.importer

import java.math.BigDecimal
import java.time.LocalDateTime

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
    fun parseRow(name: String, datetime: String, temp: String): RowResult {
        return RowResult.Malformed("not implemented")
    }
}
