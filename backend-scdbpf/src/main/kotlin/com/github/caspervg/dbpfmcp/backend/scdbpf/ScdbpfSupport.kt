package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.DecodeError
import com.github.caspervg.dbpfmcp.core.PackageError
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.semantics.formatHex32
import io.github.memo33.passera.unsigned.UInt
import io.github.memo33.scdbpf.BufferedEntry
import io.github.memo33.scdbpf.DbpfFile
import io.github.memo33.scdbpf.DbpfProperty
import io.github.memo33.scdbpf.DbpfType
import io.github.memo33.scdbpf.Exemplar
import io.github.memo33.scdbpf.Fsh
import io.github.memo33.scdbpf.LText
import io.github.memo33.scdbpf.S3d
import io.github.memo33.scdbpf.Sc4Path
import io.github.memo33.scdbpf.StreamedEntry
import io.github.memo33.scdbpf.Tgi as ScTgi
import io.github.memo33.scdbpf.compat.ExceptionHandler
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import scala.collection.immutable.Map
import scala.jdk.javaapi.CollectionConverters

/**
 * Helpers shared by [ScdbpfAdapter], [ScdbpfPluginIndexer], and [ScdbpfEntryExplainer].
 *
 * These previously existed as private copies in each of those three classes, which allowed them to
 * drift apart; several user-visible inconsistencies came from copies that had diverged.
 */

/** The single exception strategy used for every scdbpf call. */
internal val dbpfHandler: ExceptionHandler = io.github.memo33.scdbpf.`package`.strategy().throwExceptions()

internal fun readPackage(path: String): DbpfFile {
    val file = requireDbpfPackageFile(path)
    return try {
        DbpfFile.read(file, dbpfHandler) as DbpfFile
    } catch (exception: Exception) {
        throw PackageError("Failed to read DBPF package: ${file.absolutePath}", exception)
    }
}

internal fun findEntry(dbpf: DbpfFile, tgi: Tgi): StreamedEntry =
    findEntryOrNull(dbpf, tgi)
        ?: throw PackageError("Entry not found for TGI $tgi")

internal fun findEntryOrNull(dbpf: DbpfFile, tgi: Tgi): StreamedEntry? =
    CollectionConverters.asJava(dbpf.entries())
        .map { it as StreamedEntry }
        .firstOrNull { tgiToDomain(it.tgi()) == tgi }

/** An entry's decoded payload together with whether it was stored QFS-compressed. */
internal class DecodedEntry(val bytes: ByteArray, val compressed: Boolean)

/**
 * Decodes an entry's payload through scdbpf.
 *
 * DBPF entries are commonly QFS-compressed. [StreamedEntry.toRawEntry] hands back the stored
 * payload, still compressed; converting to a buffered entry is what makes scdbpf decompress it.
 * Callers that want bytes must go through here — reading `toRawEntry` and then decompressing by
 * hand (or, worse, not at all) is what `read_keycfg`, `read_tab_binary`, and `read_image_entry`
 * used to do.
 */
@Suppress("UNCHECKED_CAST")
internal fun decodeEntry(entry: StreamedEntry, tgi: Tgi): DecodedEntry = try {
    val buffered = entry.toBufferedEntry(dbpfHandler) as BufferedEntry<DbpfType>
    DecodedEntry(bytes = buffered.content().data(), compressed = buffered.compressed())
} catch (exception: Exception) {
    throw DecodeError("Failed to read entry ${formatTgi(tgi)}", exception)
}

internal fun entryBytes(entry: StreamedEntry, tgi: Tgi): ByteArray = decodeEntry(entry, tgi).bytes

@Suppress("UNCHECKED_CAST")
internal fun decodeExemplarEntry(entry: StreamedEntry): BufferedEntry<Exemplar> {
    val buffered = entry.toBufferedEntry(dbpfHandler) as BufferedEntry<DbpfType>
    return buffered.convert(dbpfHandler, Exemplar.converter()) as BufferedEntry<Exemplar>
}

@Suppress("UNCHECKED_CAST")
internal fun decodeLTextEntry(entry: StreamedEntry): BufferedEntry<LText> {
    val buffered = entry.toBufferedEntry(dbpfHandler) as BufferedEntry<DbpfType>
    return buffered.convert(dbpfHandler, LText.contentConverter()) as BufferedEntry<LText>
}

@Suppress("UNCHECKED_CAST")
internal fun decodeSc4PathEntry(entry: StreamedEntry): BufferedEntry<Sc4Path> {
    val buffered = entry.toBufferedEntry(dbpfHandler) as BufferedEntry<DbpfType>
    return buffered.convert(dbpfHandler, Sc4Path.contentConverter()) as BufferedEntry<Sc4Path>
}

@Suppress("UNCHECKED_CAST")
internal fun decodeS3dEntry(entry: StreamedEntry): BufferedEntry<S3d> {
    val buffered = entry.toBufferedEntry(dbpfHandler) as BufferedEntry<DbpfType>
    return buffered.convert(dbpfHandler, S3d.contentConverter()) as BufferedEntry<S3d>
}

@Suppress("UNCHECKED_CAST")
internal fun decodeFshEntry(entry: StreamedEntry): BufferedEntry<Fsh> {
    val buffered = entry.toBufferedEntry(dbpfHandler) as BufferedEntry<DbpfType>
    return buffered.convert(dbpfHandler, Fsh.contentConverter()) as BufferedEntry<Fsh>
}

internal fun tgiToDomain(tgi: ScTgi): Tgi = Tgi(
    type = unsignedInt(tgi.tid()),
    group = unsignedInt(tgi.gid()),
    instance = unsignedInt(tgi.iid()),
)

internal fun formatTgi(tgi: Tgi): String =
    "${formatHex32(tgi.type)}-${formatHex32(tgi.group)}-${formatHex32(tgi.instance)}"

internal fun unsignedInt(value: Any): Long = (value as Number).toLong() and 0xFFFF_FFFFL

internal fun isBlankTgi(tgi: ScTgi): Boolean =
    unsignedInt(tgi.tid()) == 0L && unsignedInt(tgi.gid()) == 0L && unsignedInt(tgi.iid()) == 0L

internal fun scalaMapEntries(
    properties: Map<UInt, DbpfProperty.PropertyList<*>>,
): List<Pair<UInt, DbpfProperty.PropertyList<*>>> =
    CollectionConverters.asJava(properties).entries.map { entry ->
        entry.key to entry.value
    }

internal fun propertyValues(propertyList: DbpfProperty.PropertyList<*>): List<JsonElement> = when (propertyList) {
    is DbpfProperty.Single<*> -> listOf(valueToJson(propertyList.value()))
    is DbpfProperty.Multi<*> -> CollectionConverters.asJava(propertyList.values()).map(::valueToJson)
    else -> listOf(JsonPrimitive(propertyList.toString()))
}

internal fun valueToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value.toLong())
    is Long -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value.toDouble())
    is Double -> JsonPrimitive(value)
    is UInt -> JsonPrimitive(value.toLong())
    is io.github.memo33.passera.unsigned.UShort -> JsonPrimitive(value.toInt())
    is ScTgi -> JsonArray(
        listOf(
            JsonPrimitive(unsignedInt(value.tid())),
            JsonPrimitive(unsignedInt(value.gid())),
            JsonPrimitive(unsignedInt(value.iid())),
        )
    )
    else -> JsonPrimitive(value.toString())
}
