package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.example.sdd.TestcontainersConfiguration

@SpringBootTest(properties = ["importer.input-dir=src/test/resources/input"])
@Import(TestcontainersConfiguration::class)
class StartupSmokeTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `context boots successfully and flyway creates the temperature_readings table`() {
        // Query to check table columns
        val columns = jdbcTemplate.query("DESCRIBE temperature_readings") { rs, _ ->
            rs.getString("Field") to rs.getString("Type")
        }.toMap()

        assertThat(columns).containsKey("id")
        assertThat(columns).containsKey("name")
        assertThat(columns).containsKey("datetime")
        assertThat(columns).containsKey("temp")

        // Check columns types are compatible (e.g. decimal, datetime)
        assertThat(columns["id"]).contains("bigint")
        assertThat(columns["name"]).contains("varchar(255)")
        assertThat(columns["datetime"]).contains("datetime")
        assertThat(columns["temp"]).contains("decimal(5,2)")
    }

    @Test
    fun `h2 database driver is not on the classpath`() {
        org.junit.jupiter.api.assertThrows<ClassNotFoundException> {
            Class.forName("org.h2.Driver")
        }
    }
}
