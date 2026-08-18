package de.ilazlow.velosonic.data.playlist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Direct Kotlin port of `SmartPlaylistCriteria.swift` — same field catalog, same operator sets
 *  per field type, and (critically) the exact same JSON shape Navidrome's native smart-playlist
 *  rules DSL expects/produces, so criteria built here round-trip with Navidrome's own web UI. */
enum class SPFieldType { TEXT, NUMBER, RATING, BOOLEAN, DATE }

data class SPOperator(val key: String, val label: String)

data class SPFieldDef(val key: String, val label: String, val type: SPFieldType) {
    val availableOps: List<SPOperator>
        get() = when (type) {
            SPFieldType.TEXT -> listOf(
                SPOperator("contains", "Contains"), SPOperator("notContains", "Doesn't contain"),
                SPOperator("is", "Is"), SPOperator("isNot", "Is not"),
                SPOperator("startsWith", "Starts with"), SPOperator("endsWith", "Ends with")
            )
            SPFieldType.NUMBER -> listOf(
                SPOperator("is", "Is"), SPOperator("isNot", "Is not"),
                SPOperator("gt", "Greater than"), SPOperator("lt", "Less than"),
                SPOperator("inTheRange", "Is in range")
            )
            SPFieldType.RATING -> listOf(
                SPOperator("is", "Is"), SPOperator("isNot", "Is not"),
                SPOperator("gt", "Greater than"), SPOperator("lt", "Less than")
            )
            SPFieldType.BOOLEAN -> listOf(SPOperator("is", "Is"))
            SPFieldType.DATE -> listOf(
                SPOperator("inTheLast", "In the last"), SPOperator("notInTheLast", "Not in the last"),
                SPOperator("before", "Before"), SPOperator("after", "After")
            )
        }

    val defaultOp: String get() = availableOps.firstOrNull()?.key ?: "is"

    companion object {
        val all: List<SPFieldDef> = listOf(
            SPFieldDef("title", "Title", SPFieldType.TEXT),
            SPFieldDef("artist", "Artist", SPFieldType.TEXT),
            SPFieldDef("album", "Album", SPFieldType.TEXT),
            SPFieldDef("genre", "Genre", SPFieldType.TEXT),
            SPFieldDef("comment", "Comment", SPFieldType.TEXT),
            SPFieldDef("year", "Year", SPFieldType.NUMBER),
            SPFieldDef("tracknumber", "Track #", SPFieldType.NUMBER),
            SPFieldDef("duration", "Duration (s)", SPFieldType.NUMBER),
            SPFieldDef("bpm", "BPM", SPFieldType.NUMBER),
            SPFieldDef("bitrate", "Bitrate", SPFieldType.NUMBER),
            SPFieldDef("playcount", "Play Count", SPFieldType.NUMBER),
            SPFieldDef("rating", "Rating (1–5)", SPFieldType.RATING),
            SPFieldDef("loved", "Loved", SPFieldType.BOOLEAN),
            SPFieldDef("hascoverart", "Has Cover Art", SPFieldType.BOOLEAN),
            SPFieldDef("compilation", "Compilation", SPFieldType.BOOLEAN),
            SPFieldDef("dateadded", "Date Added", SPFieldType.DATE),
            SPFieldDef("dateloved", "Date Loved", SPFieldType.DATE),
            SPFieldDef("lastplayed", "Last Played", SPFieldType.DATE),
            SPFieldDef("datemodified", "Date Modified", SPFieldType.DATE)
        )

        fun def(forKey: String): SPFieldDef = all.firstOrNull { it.key == forKey } ?: SPFieldDef(forKey, forKey, SPFieldType.TEXT)
    }
}

/** yyyy-MM-dd, matching the DateFormatter iOS uses for `before`/`after` rule values. */
val spRuleDateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

data class SPRule(
    val id: String = UUID.randomUUID().toString(),
    val field: String = "title",
    val op: String = "contains",
    /** String/number/days value (text representation for editing). */
    val stringValue: String = "",
    /** For boolean fields. */
    val boolValue: Boolean = true,
    /** For the inTheRange operator. */
    val rangeMin: Int = 0,
    val rangeMax: Int = 100,
    /** For the before/after operators. */
    val dateValue: Date = Date()
)

data class SPCriteria(
    val matchAll: Boolean = true,
    val rules: List<SPRule> = listOf(SPRule()),
    val sortField: String = "title",
    val sortOrder: String = "asc",
    val limitEnabled: Boolean = false,
    val limit: Int = 50
) {
    fun toJsonString(): String {
        val ruleArray = buildJsonArray {
            for (rule in rules) {
                if (rule.field.isEmpty() || rule.op.isEmpty()) continue
                val fieldDef = SPFieldDef.def(rule.field)
                val ruleValue = when (fieldDef.type) {
                    SPFieldType.BOOLEAN -> JsonPrimitive(rule.boolValue)
                    SPFieldType.NUMBER, SPFieldType.RATING ->
                        if (rule.op == "inTheRange") {
                            buildJsonArray { add(JsonPrimitive(rule.rangeMin)); add(JsonPrimitive(rule.rangeMax)) }
                        } else {
                            JsonPrimitive(rule.stringValue.toIntOrNull() ?: 0)
                        }
                    SPFieldType.DATE ->
                        if (rule.op == "inTheLast" || rule.op == "notInTheLast") {
                            JsonPrimitive(rule.stringValue.toIntOrNull() ?: 30)
                        } else {
                            JsonPrimitive(spRuleDateFormat.format(rule.dateValue))
                        }
                    SPFieldType.TEXT -> JsonPrimitive(rule.stringValue)
                }
                add(buildJsonObject { put(rule.op, buildJsonObject { put(rule.field, ruleValue) }) })
            }
        }

        val matchKey = if (matchAll) "all" else "any"
        return buildJsonObject {
            put(matchKey, ruleArray)
            if (sortField.isNotEmpty()) {
                put("sort", JsonPrimitive(sortField))
                put("order", JsonPrimitive(sortOrder))
            }
            if (limitEnabled) put("limit", JsonPrimitive(limit))
        }.toString()
    }

    companion object {
        fun fromJsonString(jsonString: String): SPCriteria? {
            val raw = runCatching { Json.parseToJsonElement(jsonString).jsonObject }.getOrNull() ?: return null

            val allArray = (raw["all"] as? JsonArray)
            val anyArray = (raw["any"] as? JsonArray)
            val matchAll = allArray != null || anyArray == null
            val ruleArray = allArray ?: anyArray ?: JsonArray(emptyList())

            val sortField = (raw["sort"] as? JsonPrimitive)?.contentOrNull ?: "title"
            val sortOrder = (raw["order"] as? JsonPrimitive)?.contentOrNull ?: "asc"
            val limitValue = (raw["limit"] as? JsonPrimitive)?.intOrNull

            val rules = ruleArray.mapNotNull { ruleElement ->
                val ruleObj = ruleElement as? JsonObject ?: return@mapNotNull null
                val op = ruleObj.keys.firstOrNull() ?: return@mapNotNull null
                val fieldObj = ruleObj[op] as? JsonObject ?: return@mapNotNull null
                val field = fieldObj.keys.firstOrNull() ?: return@mapNotNull null
                val fieldDef = SPFieldDef.def(field)
                val rawValue = fieldObj[field]
                val base = SPRule(field = field, op = op)

                when (fieldDef.type) {
                    SPFieldType.BOOLEAN -> base.copy(boolValue = (rawValue as? JsonPrimitive)?.booleanOrNull ?: true)
                    SPFieldType.NUMBER, SPFieldType.RATING -> {
                        val rangeArray = rawValue as? JsonArray
                        if (rangeArray != null && rangeArray.size == 2) {
                            base.copy(
                                rangeMin = (rangeArray[0] as? JsonPrimitive)?.intOrNull ?: 0,
                                rangeMax = (rangeArray[1] as? JsonPrimitive)?.intOrNull ?: 100
                            )
                        } else {
                            val prim = rawValue as? JsonPrimitive
                            val number = prim?.intOrNull ?: prim?.doubleOrNull?.toInt()
                            base.copy(stringValue = number?.toString() ?: prim?.contentOrNull.orEmpty())
                        }
                    }
                    SPFieldType.DATE -> {
                        val prim = rawValue as? JsonPrimitive
                        val days = prim?.intOrNull
                        if (days != null) {
                            base.copy(stringValue = days.toString())
                        } else {
                            val dateString = prim?.contentOrNull.orEmpty()
                            val parsed = runCatching { spRuleDateFormat.parse(dateString) }.getOrNull() ?: Date()
                            base.copy(stringValue = dateString, dateValue = parsed)
                        }
                    }
                    SPFieldType.TEXT -> base.copy(stringValue = (rawValue as? JsonPrimitive)?.contentOrNull.orEmpty())
                }
            }

            return SPCriteria(
                matchAll = matchAll,
                rules = rules.ifEmpty { listOf(SPRule()) },
                sortField = sortField,
                sortOrder = sortOrder,
                limitEnabled = limitValue != null,
                limit = limitValue ?: 50
            )
        }
    }
}
