package org.example.sdd.importer

import org.springframework.stereotype.Component
import java.text.Normalizer
import java.time.LocalDateTime
import java.util.Locale

/**
 * RunDeduplicator deduplicates temperature readings in memory during a single run of the importer.
 *
 * It uses a set of normalized names and datetimes to detect duplicate rows across all files in the current run.
 * The name is normalized using [Normalizer.NFD] to strip accents/combining marks and [Locale.ROOT] lowercase
 * to approximate MySQL's utf8mb4_0900_ai_ci collation.
 *
 * Known Divergences from utf8mb4_0900_ai_ci:
 * - German 'ß' (sharp s) does not normalize to 'ss' in in-memory NFD/lowercase(Locale.ROOT), but MySQL treats them as equal.
 * - Turkish dotted/dotless 'I' / 'i' may map differently in Locale.ROOT vs Turkish locale collation (e.g. 'İ' normalizes to 'i' + combining mark, then marks are stripped).
 * - Half-width and full-width Japanese katakana might not be fully unified by NFD/lowercase alone, whereas MySQL's collation may treat them as equal.
 */
@Component
class RunDeduplicator {
    private val seen = mutableSetOf<Pair<String, LocalDateTime>>()

    @Synchronized
    fun seenOrAdd(name: String, datetime: LocalDateTime): Boolean {
        val normalizedName = normalizeName(name)
        val key = normalizedName to datetime
        return if (seen.contains(key)) {
            true
        } else {
            seen.add(key)
            false
        }
    }

    fun normalizeName(name: String): String {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.ROOT)
    }

    @Synchronized
    fun clear() {
        seen.clear()
    }
}
