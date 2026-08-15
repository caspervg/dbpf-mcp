package com.github.caspervg.dbpfmcp.server

import com.github.caspervg.dbpfmcp.core.EntrySummary
import com.github.caspervg.dbpfmcp.core.ExemplarModel
import com.github.caspervg.dbpfmcp.core.ExemplarProperty
import com.github.caspervg.dbpfmcp.core.ExplainEntryResult
import com.github.caspervg.dbpfmcp.core.ListEntriesResult
import com.github.caspervg.dbpfmcp.core.ParentChainItem
import com.github.caspervg.dbpfmcp.core.PropertyDescription
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.semantics.formatHex32
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Verbatim copies of the hand-written encoders that `Main.kt` used before the wire layer moved to
 * kotlinx.serialization.
 *
 * They exist only so [WireFormatCompatibilityTest] can prove the generated encoding still produces
 * the same field names, the same hexadecimal identifier formatting, and the same values. Do not
 * add to this file or use it outside that test; when the wire format is next changed on purpose,
 * update the test's expectations rather than these functions.
 */
object LegacyEncoders {

    fun tgiJson(tgi: Tgi): JsonObject = buildJsonObject {
        put("type", formatHex32(tgi.type))
        put("group", formatHex32(tgi.group))
        put("instance", formatHex32(tgi.instance))
    }

    fun propertyJson(property: ExemplarProperty): JsonObject = buildJsonObject {
        put("id", formatHex32(property.id))
        put("name", property.name?.let(::JsonPrimitive) ?: JsonNull)
        put("valueType", property.valueType)
        put("expectedType", property.expectedType?.let(::JsonPrimitive) ?: JsonNull)
        put("typeMatchesExpected", property.typeMatchesExpected?.let(::JsonPrimitive) ?: JsonNull)
        putJsonArray("values") {
            property.values.forEach(::add)
        }
        putJsonArray("decodedValues") {
            property.decodedValues?.forEach { value ->
                add(
                    buildJsonObject {
                        put("index", value.index)
                        put("raw", value.raw)
                        put("normalized", value.normalized)
                        put("decimal", value.decimal?.let(::JsonPrimitive) ?: JsonNull)
                        put("hex", value.hex?.let(::JsonPrimitive) ?: JsonNull)
                        put("text", value.text?.let(::JsonPrimitive) ?: JsonNull)
                        put("boolean", value.boolean?.let(::JsonPrimitive) ?: JsonNull)
                        put("label", value.label?.let(::JsonPrimitive) ?: JsonNull)
                    }
                )
            }
        }
        put("semanticType", property.semanticType?.let(::JsonPrimitive) ?: JsonNull)
        put("interpretation", property.interpretation ?: JsonNull)
    }

    fun parentChainItemJson(item: ParentChainItem): JsonObject = buildJsonObject {
        put("tgi", tgiJson(item.tgi))
        put("name", item.name?.let(::JsonPrimitive) ?: JsonNull)
        put("propertyCount", item.propertyCount?.let(::JsonPrimitive) ?: JsonNull)
        put("resolved", item.resolved)
        put("sourcePackagePath", item.sourcePackagePath?.let(::JsonPrimitive) ?: JsonNull)
        put("warning", item.warning?.let(::JsonPrimitive) ?: JsonNull)
    }

    fun exemplarJson(model: ExemplarModel): JsonObject = buildJsonObject {
        put("tgi", tgiJson(model.tgi))
        put("parentCohort", model.parentCohort?.let(::tgiJson) ?: JsonNull)
        put("exemplarName", model.exemplarName?.let(::JsonPrimitive) ?: JsonNull)
        putJsonArray("properties") {
            model.properties.forEach { add(propertyJson(it)) }
        }
        putJsonArray("parentChain") {
            model.parentChain.forEach { add(parentChainItemJson(it)) }
        }
    }

    fun entrySummaryJson(entry: EntrySummary): JsonObject = buildJsonObject {
        put("tgi", tgiJson(entry.tgi))
        put("kind", entry.kind.name)
        put("size", entry.size?.let(::JsonPrimitive) ?: JsonNull)
        put("compressed", entry.compressed?.let(::JsonPrimitive) ?: JsonNull)
        put("label", entry.label?.let(::JsonPrimitive) ?: JsonNull)
    }

    fun listEntriesJson(result: ListEntriesResult): JsonObject = buildJsonObject {
        put("packagePath", result.packagePath)
        put("entryCount", result.entryCount)
        putJsonArray("entries") {
            result.entries.forEach { add(entrySummaryJson(it)) }
        }
    }

    fun propertyDescriptionJson(description: PropertyDescription): JsonObject = buildJsonObject {
        put("id", formatHex32(description.id))
        put("name", description.name)
        put("type", description.type?.let(::JsonPrimitive) ?: JsonNull)
        put("description", description.description?.let(::JsonPrimitive) ?: JsonNull)
        put("group", description.group?.let(::JsonPrimitive) ?: JsonNull)
    }

    fun explanationJson(result: ExplainEntryResult): JsonObject = buildJsonObject {
        put("packagePath", result.packagePath)
        put("tgi", tgiJson(result.tgi))
        put("kind", result.kind.name)
        put("summary", result.summary)
        putJsonArray("importantFields") {
            result.importantFields.forEach { field ->
                add(
                    buildJsonObject {
                        put("name", field.name)
                        put("value", field.value)
                    }
                )
            }
        }
        putJsonArray("relationships") {
            result.relationships.forEach { relationship ->
                add(
                    buildJsonObject {
                        put("kind", relationship.kind)
                        put("tgi", relationship.tgi?.let(::tgiJson) ?: JsonNull)
                        put("label", relationship.label?.let(::JsonPrimitive) ?: JsonNull)
                        put("resolved", relationship.resolved?.let(::JsonPrimitive) ?: JsonNull)
                    }
                )
            }
        }
        putJsonArray("warnings") {
            result.warnings.forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("suggestedNextTools") {
            result.suggestedNextTools.forEach { add(JsonPrimitive(it)) }
        }
    }
}
