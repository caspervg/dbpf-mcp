package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.semantics.propertyName
import io.github.memo33.passera.unsigned.UByte
import io.github.memo33.passera.unsigned.UInt
import io.github.memo33.passera.unsigned.UShort
import io.github.memo33.scdbpf.DbpfProperty
import io.github.memo33.scdbpf.Exemplar
import scala.jdk.javaapi.CollectionConverters
import java.nio.charset.StandardCharsets

internal object TextExemplarRenderer {
    const val FORMAT = "canonical-text-exemplar"

    fun render(exemplar: Exemplar): String = buildString {
        append(if (exemplar.isCohort()) "CQZT1###" else "EQZT1###")
        append(CRLF)
        append("ParentCohort=Key:{")
        append(formatSigned32(exemplar.parent().tid()))
        append(',')
        append(formatSigned32(exemplar.parent().gid()))
        append(',')
        append(formatSigned32(exemplar.parent().iid()))
        append('}')
        append(CRLF)
        append("PropCount=")
        append(CollectionConverters.asJava(exemplar.properties()).size)
        append(CRLF)
        append(CRLF)

        CollectionConverters.asJava(exemplar.properties()).entries.forEach { entry ->
            append(formatProperty(entry.key, entry.value))
            append(CRLF)
        }
    }

    private fun formatProperty(id: UInt, property: DbpfProperty.PropertyList<*>): String {
        val values = propertyValues(property)
        val propertyId = id.toLong() and 0xFFFF_FFFFL
        val description = textLiteral(propertyName(propertyId) ?: formatUnsignedHex32(propertyId))
        val type = property.valueType().toString()
        val repetitions = if (property is DbpfProperty.Single<*> && type != "String") {
            0
        } else {
            values.size
        }

        return if (type == "String") {
            val value = values.firstOrNull() as? String ?: ""
            // UTF-8, not US-ASCII: encoding as ASCII collapses every non-ASCII character to '?',
            // which silently produced the wrong declared byte length for non-ASCII names.
            val byteLength = value.toByteArray(StandardCharsets.UTF_8).size
            "${formatSigned32(id)}:$description=String:$byteLength:${textLiteral(value)}"
        } else {
            "${formatSigned32(id)}:$description=$type:$repetitions:{${values.joinToString(",") { formatValue(it) }}}"
        }
    }

    private fun propertyValues(property: DbpfProperty.PropertyList<*>): List<Any?> = when (property) {
        is DbpfProperty.Single<*> -> listOf(property.value())
        is DbpfProperty.Multi<*> -> CollectionConverters.asJava(property.values()).toList()
        else -> listOf(property.toString())
    }

    private fun textLiteral(value: String): String {
        if (value.contains("\"}")) {
            // InputError, not IllegalArgumentException: this is a caller-visible limitation and
            // has to stay inside the typed error hierarchy to be reported as such.
            throw InputError("Text exemplar syntax cannot represent strings containing the sequence \"}")
        }
        return "{\"$value\"}"
    }

    private fun formatValue(value: Any?): String = when (value) {
        null -> "0"
        is Boolean -> value.toString()
        is Float -> value.toString()
        is Double -> value.toString()
        is UByte -> value.toLong().toString()
        is UShort -> value.toLong().toString()
        is UInt -> formatUnsignedHex32(value.toLong() and 0xFFFF_FFFFL)
        is Int -> value.toString()
        is Long -> value.toString()
        is Number -> value.toLong().toString()
        else -> value.toString()
    }

    private fun formatSigned32(value: Any): String = when (value) {
        is UInt -> value.toLong().toInt().toString()
        is UShort -> value.toLong().toInt().toString()
        is UByte -> value.toLong().toInt().toString()
        is Number -> value.toInt().toString()
        else -> value.toString().toInt().toString()
    }

    private fun formatUnsignedHex32(value: Long): String =
        "0x" + value.toULong().toString(16).uppercase().padStart(8, '0')

    private const val CRLF = "\r\n"
}
