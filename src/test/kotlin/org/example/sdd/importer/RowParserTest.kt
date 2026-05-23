package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class RowParserTest {

    @Test
    fun `should parse valid ISO T-separated datetime and space-separated datetime`() {
        val resultT = RowParser.parseRow("Station A", "2026-05-23T09:15:30", "23.45")
        assertThat(resultT).isInstanceOf(RowResult.Valid::class.java)
        val validT = resultT as RowResult.Valid
        assertThat(validT.name).isEqualTo("Station A")
        assertThat(validT.datetime).isEqualTo(LocalDateTime.of(2026, 5, 23, 9, 15, 30))
        assertThat(validT.temp).isEqualTo(BigDecimal("23.45"))

        val resultSpace = RowParser.parseRow("Station B", "2026-05-23 09:15:30.123", "23.4")
        assertThat(resultSpace).isInstanceOf(RowResult.Valid::class.java)
        val validSpace = resultSpace as RowResult.Valid
        assertThat(validSpace.name).isEqualTo("Station B")
        assertThat(validSpace.datetime).isEqualTo(LocalDateTime.of(2026, 5, 23, 9, 15, 30, 123000000))
        assertThat(validSpace.temp).isEqualTo(BigDecimal("23.40")) // setScale(2, UNNECESSARY)
    }

    @Test
    fun `should accept temperature boundaries within or at maximum range`() {
        // 999.98 within range
        val result1 = RowParser.parseRow("Station A", "2026-05-23 09:15:30", "999.98")
        assertThat(result1).isInstanceOf(RowResult.Valid::class.java)
        assertThat((result1 as RowResult.Valid).temp).isEqualTo(BigDecimal("999.98"))

        // 999.99 inclusive maximum
        val result2 = RowParser.parseRow("Station A", "2026-05-23 09:15:30", "999.99")
        assertThat(result2).isInstanceOf(RowResult.Valid::class.java)
        assertThat((result2 as RowResult.Valid).temp).isEqualTo(BigDecimal("999.99"))

        // -999.99 inclusive minimum
        val result3 = RowParser.parseRow("Station A", "2026-05-23 09:15:30", "-999.99")
        assertThat(result3).isInstanceOf(RowResult.Valid::class.java)
        assertThat((result3 as RowResult.Valid).temp).isEqualTo(BigDecimal("-999.99"))
    }

    @Test
    fun `should reject temperature beyond maximum range`() {
        // 1000.00 is beyond DECIMAL(5,2)
        val result1 = RowParser.parseRow("Station A", "2026-05-23 09:15:30", "1000.00")
        assertThat(result1).isInstanceOf(RowResult.Malformed::class.java)
        assertThat((result1 as RowResult.Malformed).reason).isEqualTo("temp out of range")

        // -1000.00 is beyond DECIMAL(5,2)
        val result2 = RowParser.parseRow("Station A", "2026-05-23 09:15:30", "-1000.00")
        assertThat(result2).isInstanceOf(RowResult.Malformed::class.java)
        assertThat((result2 as RowResult.Malformed).reason).isEqualTo("temp out of range")

        // More than 2 decimal places e.g. 99.999
        val result3 = RowParser.parseRow("Station A", "2026-05-23 09:15:30", "99.999")
        assertThat(result3).isInstanceOf(RowResult.Malformed::class.java)
        assertThat((result3 as RowResult.Malformed).reason).isEqualTo("temp out of range")

        // Precision check e.g. 1000 has 4 integer digits
        val result4 = RowParser.parseRow("Station A", "2026-05-23 09:15:30", "1000")
        assertThat(result4).isInstanceOf(RowResult.Malformed::class.java)
        assertThat((result4 as RowResult.Malformed).reason).isEqualTo("temp out of range")
    }

    @Test
    fun `should accept name boundaries up to 255 characters`() {
        val name254 = "A".repeat(254)
        val result254 = RowParser.parseRow(name254, "2026-05-23 09:15:30", "23.45")
        assertThat(result254).isInstanceOf(RowResult.Valid::class.java)
        assertThat((result254 as RowResult.Valid).name).isEqualTo(name254)

        val name255 = "A".repeat(255)
        val result255 = RowParser.parseRow(name255, "2026-05-23 09:15:30", "23.45")
        assertThat(result255).isInstanceOf(RowResult.Valid::class.java)
        assertThat((result255 as RowResult.Valid).name).isEqualTo(name255)

        val name256 = "A".repeat(256)
        val result256 = RowParser.parseRow(name256, "2026-05-23 09:15:30", "23.45")
        assertThat(result256).isInstanceOf(RowResult.Malformed::class.java)
        assertThat((result256 as RowResult.Malformed).reason).isEqualTo("name too long")
    }

    @Test
    fun `should trim name and validate`() {
        val paddedName = "   Station A   "
        val result = RowParser.parseRow(paddedName, "2026-05-23 09:15:30", "23.45")
        assertThat(result).isInstanceOf(RowResult.Valid::class.java)
        assertThat((result as RowResult.Valid).name).isEqualTo("Station A")
    }

    @Test
    fun `should check empty required fields`() {
        val r1 = RowParser.parseRow("  ", "2026-05-23 09:15:30", "23.45")
        assertThat(r1).isInstanceOf(RowResult.Malformed::class.java)
        assertThat((r1 as RowResult.Malformed).reason).isEqualTo("empty name")

        val r2 = RowParser.parseRow("Station A", "  ", "23.45")
        assertThat(r2).isInstanceOf(RowResult.Malformed::class.java)
        assertThat((r2 as RowResult.Malformed).reason).isEqualTo("empty datetime")

        val r3 = RowParser.parseRow("Station A", "2026-05-23 09:15:30", "  ")
        assertThat(r3).isInstanceOf(RowResult.Malformed::class.java)
        assertThat((r3 as RowResult.Malformed).reason).isEqualTo("empty temp")
    }

    @Test
    fun `should check unparseable datetime`() {
        val r = RowParser.parseRow("Station A", "2026-05-23 09-15-30", "23.45")
        assertThat(r).isInstanceOf(RowResult.Malformed::class.java)
        assertThat((r as RowResult.Malformed).reason).isEqualTo("unparseable datetime")
    }

    @Test
    fun `should check non-numeric temp`() {
        val r = RowParser.parseRow("Station A", "2026-05-23 09:15:30", "abc")
        assertThat(r).isInstanceOf(RowResult.Malformed::class.java)
        assertThat((r as RowResult.Malformed).reason).isEqualTo("non-numeric temp")
    }
}
