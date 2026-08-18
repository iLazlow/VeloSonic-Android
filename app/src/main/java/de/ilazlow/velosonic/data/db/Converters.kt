package de.ilazlow.velosonic.data.db

import androidx.room.TypeConverter

/**
 * Real `List<String>` columns via a TypeConverter — the iOS client stores these as
 * comma- or pipe-joined strings because SwiftData has no array-of-primitives column support
 * on most fields. Room doesn't have that limitation, so there's no need to replicate the
 * CSV workaround (or its ambiguity when a genre/artist name itself contains the delimiter).
 * Unit Separator (code point 31) is used precisely because it can't appear in real metadata
 * text — built from its code point rather than a literal escape so the exact character
 * written to this file is never ambiguous.
 */
object Converters {
    private val DELIMITER = 31.toChar().toString()

    @TypeConverter
    fun listToString(list: List<String>?): String? = list?.joinToString(DELIMITER)

    @TypeConverter
    fun stringToList(value: String?): List<String>? =
        value?.let { if (it.isEmpty()) emptyList() else it.split(DELIMITER) }
}
