package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.example.sdd.TestcontainersConfiguration
import java.math.BigDecimal
import java.time.LocalDateTime
import org.springframework.dao.DataAccessException

@SpringBootTest(properties = ["importer.input-dir=src/test/resources/input"])
@Import(TestcontainersConfiguration::class)
class TemperatureReadingRepositoryTest {

    @Autowired
    private lateinit var repository: TemperatureReadingRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM temperature_readings")
    }

    @Test
    fun `insertIfAbsent inserts new row and returns Inserted`() {
        val name = "Test Location"
        val datetime = LocalDateTime.of(2026, 5, 23, 12, 0, 0)
        val temp = BigDecimal("23.45")

        val outcome = repository.insertIfAbsent(name, datetime, temp)

        assertThat(outcome).isEqualTo(InsertOutcome.Inserted)

        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM temperature_readings", Int::class.java)
        assertThat(count).isEqualTo(1)

        val savedTemp = jdbcTemplate.queryForObject(
            "SELECT temp FROM temperature_readings WHERE name = ? AND datetime = ?",
            BigDecimal::class.java, name, datetime
        )
        assertThat(savedTemp).isEqualByComparingTo(temp)
    }

    @Test
    fun `insertIfAbsent returns DuplicateInDb for duplicate and does not insert a new row`() {
        val name = "Test Location"
        val datetime = LocalDateTime.of(2026, 5, 23, 12, 0, 0)
        val temp = BigDecimal("23.45")

        // First insert
        val outcome1 = repository.insertIfAbsent(name, datetime, temp)
        assertThat(outcome1).isEqualTo(InsertOutcome.Inserted)

        // Second insert (same key, different temp to check if it's ignored/upserted)
        val outcome2 = repository.insertIfAbsent(name, datetime, BigDecimal("24.50"))
        assertThat(outcome2).isEqualTo(InsertOutcome.DuplicateInDb)

        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM temperature_readings", Int::class.java)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `insertIfAbsent propagates other SQLExceptions as DataAccessException`() {
        val name = "A".repeat(300) // Column is VARCHAR(255), so this will cause truncation or data too long error
        val datetime = LocalDateTime.of(2026, 5, 23, 12, 0, 0)
        val temp = BigDecimal("23.45")

        assertThrows<DataAccessException> {
            repository.insertIfAbsent(name, datetime, temp)
        }
    }

    @Autowired
    private lateinit var dataSource: javax.sql.DataSource

    @Test
    fun `assert JDBC connection uses useAffectedRows=true`() {
        assertThat(dataSource).isInstanceOf(com.zaxxer.hikari.HikariDataSource::class.java)
        val hikariDataSource = dataSource as com.zaxxer.hikari.HikariDataSource
        val useAffectedRows = hikariDataSource.dataSourceProperties["useAffectedRows"]
        assertThat(useAffectedRows).isEqualTo("true")
    }
}
