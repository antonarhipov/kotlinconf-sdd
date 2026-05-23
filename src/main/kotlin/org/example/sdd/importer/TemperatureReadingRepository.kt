package org.example.sdd.importer

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

enum class InsertOutcome {
    Inserted,
    DuplicateInDb
}

@Repository
class TemperatureReadingRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun insertIfAbsent(name: String, datetime: LocalDateTime, temp: BigDecimal): InsertOutcome {
        val params = mapOf(
            "name" to name,
            "datetime" to datetime,
            "temp" to temp
        )
        val sql = "INSERT INTO temperature_readings (name, datetime, temp) VALUES (:name, :datetime, :temp) ON DUPLICATE KEY UPDATE id = id"
        val updateCount = jdbcTemplate.update(sql, params)
        return if (updateCount == 1) {
            InsertOutcome.Inserted
        } else {
            InsertOutcome.DuplicateInDb
        }
    }
}
