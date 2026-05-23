package org.example.sdd.importer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RunDeduplicatorTest {

    private val deduplicator = RunDeduplicator()

    @Test
    fun `Café and cafe collide due to accent-insensitive normalization`() {
        val dt = LocalDateTime.of(2026, 5, 23, 12, 0, 0)
        
        val seen1 = deduplicator.seenOrAdd("Café", dt)
        assertThat(seen1).isFalse() // first time, not seen

        val seen2 = deduplicator.seenOrAdd("cafe", dt)
        assertThat(seen2).isTrue() // duplicate!
    }

    @Test
    fun `same key in different files collides`() {
        val dt = LocalDateTime.of(2026, 5, 23, 12, 0, 0)
        
        val seen1 = deduplicator.seenOrAdd("London", dt)
        assertThat(seen1).isFalse()

        // Same key, simulating second file processing
        val seen2 = deduplicator.seenOrAdd("London", dt)
        assertThat(seen2).isTrue()
    }

    @Test
    fun `different datetime does not collide`() {
        val dt1 = LocalDateTime.of(2026, 5, 23, 12, 0, 0)
        val dt2 = LocalDateTime.of(2026, 5, 23, 13, 0, 0)

        val seen1 = deduplicator.seenOrAdd("London", dt1)
        assertThat(seen1).isFalse()

        val seen2 = deduplicator.seenOrAdd("London", dt2)
        assertThat(seen2).isFalse()
    }

    @Test
    fun `German character s-sharp behavior in-memory vs db`() {
        val dt = LocalDateTime.of(2026, 5, 23, 12, 0, 0)
        
        // In our in-memory deduplicator, "MASSE" and "maße" do NOT collide because 'ß' doesn't normalize to 'ss' under NFD + Locale.ROOT
        val seen1 = deduplicator.seenOrAdd("MASSE", dt)
        assertThat(seen1).isFalse()

        val seen2 = deduplicator.seenOrAdd("maße", dt)
        assertThat(seen2).isFalse() // In-memory they don't collide
    }

    @Test
    fun `Turkish dotless and dotted I behavior`() {
        val dt = LocalDateTime.of(2026, 5, 23, 12, 0, 0)
        
        // In Locale.ROOT, 'İ' normalizes and strips mark to 'i'
        val seen1 = deduplicator.seenOrAdd("İstanbul", dt)
        assertThat(seen1).isFalse()

        val seen2 = deduplicator.seenOrAdd("istanbul", dt)
        assertThat(seen2).isTrue() // Collides under NFD + strip marks + lowercase
    }
}
