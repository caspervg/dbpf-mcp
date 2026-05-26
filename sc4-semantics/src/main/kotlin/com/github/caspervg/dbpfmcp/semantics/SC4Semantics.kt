package com.github.caspervg.dbpfmcp.semantics

import com.github.caspervg.dbpfmcp.core.DecodedPropertyModel
import com.github.caspervg.dbpfmcp.core.DecodedPropertyValue
import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import com.github.caspervg.dbpfmcp.core.PropertyDescription
import com.github.caspervg.dbpfmcp.core.Tgi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object SC4TypeIds {
    const val EXEMPLAR: Long = 0x6534284AL
    const val COHORT: Long = 0x05342861L
    const val LTEXT: Long = 0x2026960BL
    const val PNG: Long = 0x856DDBACL
    const val KEYCFG: Long = 0xA2E3D533L
    const val TAB: Long = 0xAA5C3144L
    const val RUL: Long = 0x0A5BCF4BL
    const val EFFDIR: Long = 0xEA5118B0L
    const val FSH: Long = 0x7AB50E44L
    const val S3D: Long = 0x5AD0E817L
    const val SC4PATHS: Long = 0x296678F7L
}

private val knownTypeKinds = mapOf(
    SC4TypeIds.EXEMPLAR to KnownEntryKind.EXEMPLAR,
    SC4TypeIds.COHORT to KnownEntryKind.COHORT,
    SC4TypeIds.LTEXT to KnownEntryKind.LTEXT,
    SC4TypeIds.PNG to KnownEntryKind.PNG,
    SC4TypeIds.KEYCFG to KnownEntryKind.KEYCFG,
    SC4TypeIds.TAB to KnownEntryKind.TAB,
    SC4TypeIds.RUL to KnownEntryKind.RUL,
    SC4TypeIds.EFFDIR to KnownEntryKind.EFFDIR,
    SC4TypeIds.FSH to KnownEntryKind.FSH,
    SC4TypeIds.S3D to KnownEntryKind.S3D,
    SC4TypeIds.SC4PATHS to KnownEntryKind.SC4PATHS,
)

private val exemplarTypeLabels = mapOf(
    0x00000001L to "Cohort",
    0x00000002L to "Exemplar",
    0x0000000AL to "Lot Configuration",
    0x0000000BL to "Network",
    0x0000000DL to "Lighting",
    0x0000000FL to "Lot Retaining Wall",
    0x00000010L to "Vehicle",
    0x00000011L to "Pedestrian",
    0x00000012L to "Aircraft",
    0x0000001EL to "Prop",
    0x0000001FL to "Construction",
    0x00000020L to "Automata Tuning",
    0x00000021L to "Network Lot (T21)",
    0x00000022L to "Disaster",
    0x00000023L to "Data View",
    0x00000024L to "Crime",
    0x00000025L to "Audio",
    0x00000027L to "God Mode",
    0x00000028L to "Mayor Mode",
    0x0000002AL to "Trend Bar",
    0x0000002BL to "Graph Control",
)

private val resourceKeyPropertyIds = setOf(
    0x27812820L,
    0x27812821L,
    0x27812822L,
    0x2781282AL,
    0x27812832L,
)

fun kindForType(type: Long): KnownEntryKind = knownTypeKinds[type] ?: KnownEntryKind.UNKNOWN

fun propertyDefinition(propertyId: Long): PropertyDefinition? = PropertyRegistry.instance.propertyById(propertyId)

fun propertyName(propertyId: Long): String? = propertyDefinition(propertyId)?.name

fun describeProperty(propertyId: Long): PropertyDescription? = propertyDefinition(propertyId)?.let { definition ->
    PropertyDescription(
        id = definition.id,
        name = definition.name,
        type = definition.type,
        description = definition.description,
        group = definition.group,
    )
}

fun canonicalPropertyType(type: String?): String? = when (type?.trim()) {
    null, "" -> null
    "UInt32", "Uint32", "Unit 32", "Unit32" -> "Uint32"
    "UInt8", "Uint8", "Unit8" -> "Uint8"
    "Bool" -> "Bool"
    "Float32" -> "Float32"
    "Sint32" -> "Sint32"
    "Sint64" -> "Sint64"
    "String" -> "String"
    else -> type.trim()
}

fun typesAreCompatible(actualType: String, expectedType: String?): Boolean? {
    val canonicalExpected = canonicalPropertyType(expectedType) ?: return null
    val canonicalActual = canonicalPropertyType(actualType) ?: actualType
    return canonicalActual == canonicalExpected
}

fun decodePropertyValue(propertyId: Long, values: List<JsonElement>): DecodedPropertyModel? {
    val property = describeProperty(propertyId) ?: return null
    val decodedValues = values.mapIndexed { index, raw ->
        decodeValue(index, raw, canonicalPropertyType(property.type), propertyId)
    }
    val semanticInterpretation = semanticInterpretation(propertyId, decodedValues)
    return DecodedPropertyModel(
        property = property,
        values = decodedValues,
        semanticType = semanticInterpretation?.first,
        interpretation = semanticInterpretation?.second,
    )
}

fun parseHexId(value: String, fieldName: String): Long {
    val normalized = value.trim().removePrefix("0x").removePrefix("0X")
    if (normalized.isEmpty()) {
        throw InputError("$fieldName must not be empty")
    }
    return normalized.toULongOrNull(16)?.toLong()
        ?: throw InputError("Invalid hexadecimal value for $fieldName: $value")
}

fun parseTgi(text: String): Tgi {
    val parts = text.split('-', ':', '/').map(String::trim).filter(String::isNotEmpty)
    if (parts.size != 3) {
        throw InputError("TGI must contain exactly 3 hexadecimal parts")
    }
    return Tgi(
        type = parseHexId(parts[0], "type"),
        group = parseHexId(parts[1], "group"),
        instance = parseHexId(parts[2], "instance"),
    )
}

fun formatHex32(value: Long): String = value.toULong().toString(16).uppercase().padStart(8, '0')

fun formatHex64(value: Long): String = value.toULong().toString(16).uppercase().padStart(16, '0')

fun maybeExemplarName(propertyId: Long): Boolean = propertyId == 0x00000020L

private fun decodeValue(index: Int, raw: JsonElement, expectedType: String?, propertyId: Long): DecodedPropertyValue = when (expectedType) {
    "String" -> DecodedPropertyValue(
        index = index,
        raw = raw,
        normalized = JsonPrimitive(raw.jsonPrimitive.content),
        text = raw.jsonPrimitive.content,
    )
    "Bool" -> {
        val boolValue = raw.jsonPrimitive.booleanOrNull ?: parseBooleanValue(raw.jsonPrimitive.content)
        DecodedPropertyValue(
            index = index,
            raw = raw,
            normalized = JsonPrimitive(boolValue),
            decimal = if (boolValue) 1 else 0,
            hex = formatHex32(if (boolValue) 1 else 0),
            boolean = boolValue,
        )
    }
    "Float32" -> {
        val doubleValue = raw.jsonPrimitive.doubleOrNull
            ?: throw InputError("Value at index $index is not a valid Float32: ${raw.jsonPrimitive.content}")
        DecodedPropertyValue(
            index = index,
            raw = raw,
            normalized = JsonPrimitive(doubleValue),
        )
    }
    "Uint8", "Uint32", "Sint32", "Sint64" -> {
        val numericValue = parseIntegralValue(raw.jsonPrimitive.content, index)
        DecodedPropertyValue(
            index = index,
            raw = raw,
            normalized = JsonPrimitive(numericValue),
            decimal = numericValue,
            hex = formatHex32(numericValue),
            label = numericLabel(propertyId, numericValue),
        )
    }
    else -> DecodedPropertyValue(
        index = index,
        raw = raw,
        normalized = raw,
    )
}

private fun parseIntegralValue(value: String, index: Int): Long {
    val normalized = value.trim()
    return when {
        normalized.startsWith("0x", ignoreCase = true) -> parseHexId(normalized, "values[$index]")
        else -> normalized.toLongOrNull()
            ?: throw InputError("Value at index $index is not a valid integer: $value")
    }
}

private fun parseBooleanValue(value: String): Boolean = when (value.trim().lowercase()) {
    "true", "1" -> true
    "false", "0" -> false
    else -> throw InputError("Value is not a valid Bool: $value")
}

private fun numericLabel(propertyId: Long, value: Long): String? = when (propertyId) {
    0x00000010L -> exemplarTypeLabels[value]
    else -> null
}

private fun semanticInterpretation(
    propertyId: Long,
    values: List<DecodedPropertyValue>,
): Pair<String, JsonElement>? {
    if (propertyId == 0x00000010L) {
        val value = values.firstOrNull()?.decimal ?: return null
        return "exemplarType" to buildJsonObject {
            put("value", formatHex32(value))
            put("label", exemplarTypeLabels[value] ?: "Unknown")
        }
    }
    if (propertyId in resourceKeyPropertyIds && values.size >= 3 && values.size % 3 == 0) {
        val keys = values.chunked(3).map { chunk ->
            val type = chunk[0].decimal ?: 0L
            val group = chunk[1].decimal ?: 0L
            val instance = chunk[2].decimal ?: 0L
            buildJsonObject {
                put("tgi", buildJsonObject {
                    put("type", formatHex32(type))
                    put("group", formatHex32(group))
                    put("instance", formatHex32(instance))
                })
                put("kind", kindForType(type).name)
            }
        }
        return "resourceKeyList" to kotlinx.serialization.json.JsonArray(keys)
    }
    return null
}
