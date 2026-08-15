package com.github.caspervg.dbpfmcp.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Hexadecimal formatting for the identifiers SC4 uses, and the serializers that keep them
 * hexadecimal on the wire.
 *
 * TGI components and property IDs are held as [Long] because they are compared and range-checked
 * arithmetically, but they are meaningless to a reader in decimal: `1697577546` is the exemplar
 * type ID that every SC4 tool and forum post calls `6534284A`. Encoding a model directly would
 * emit the decimal form, so the fields that are hexadecimal by convention carry
 * `@Serializable(with = Hex32Serializer::class)`.
 *
 * This lives in `core-api` rather than `sc4-semantics` so it sits below every other module; the
 * semantics module delegates to it, making this the codebase's only hex formatter.
 */

/**
 * Formats the low 32 bits of [value] as 8 uppercase hex digits.
 *
 * The mask matters for negative values: padding cannot truncate, so an unmasked `-1` renders as
 * the 16-digit "FFFFFFFFFFFFFFFF" in a field named for 32 bits.
 */
fun formatHex32(value: Long): String =
    (value and 0xFFFF_FFFFL).toString(16).uppercase().padStart(8, '0')

fun formatHex64(value: Long): String = value.toULong().toString(16).uppercase().padStart(16, '0')

/** Parses a hex identifier, tolerating an optional `0x` prefix and surrounding whitespace. */
fun parseHex(value: String, fieldName: String): Long {
    val normalized = value.trim().removePrefix("0x").removePrefix("0X")
    if (normalized.isEmpty()) {
        throw InputError("$fieldName must not be empty")
    }
    return normalized.toULongOrNull(16)?.toLong()
        ?: throw InputError("Invalid hexadecimal value for $fieldName: $value")
}

/** Encodes a [Long] as an 8-digit hex string, and reads one back. */
object Hex32Serializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.github.caspervg.dbpfmcp.Hex32", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeString(formatHex32(value))

    override fun deserialize(decoder: Decoder): Long = parseHex(decoder.decodeString(), "hex value")
}

/** Encodes a [Long] as a 16-digit hex string, and reads one back. */
object Hex64Serializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.github.caspervg.dbpfmcp.Hex64", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeString(formatHex64(value))

    override fun deserialize(decoder: Decoder): Long = parseHex(decoder.decodeString(), "hex value")
}
