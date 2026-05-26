package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.ExplainEntryRequest
import com.github.caspervg.dbpfmcp.core.ExplainEntryResult
import com.github.caspervg.dbpfmcp.core.ExplanationField
import com.github.caspervg.dbpfmcp.core.ExplanationRelationship
import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import com.github.caspervg.dbpfmcp.core.PackageError
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.semantics.SC4TypeIds
import com.github.caspervg.dbpfmcp.semantics.formatHex32
import com.github.caspervg.dbpfmcp.semantics.kindForType
import io.github.memo33.passera.unsigned.UInt
import io.github.memo33.passera.unsigned.UShort
import io.github.memo33.scdbpf.BufferedEntry
import io.github.memo33.scdbpf.DbpfFile
import io.github.memo33.scdbpf.DbpfProperty
import io.github.memo33.scdbpf.DbpfType
import io.github.memo33.scdbpf.Exemplar
import io.github.memo33.scdbpf.Fsh
import io.github.memo33.scdbpf.LText
import io.github.memo33.scdbpf.RawEntry
import io.github.memo33.scdbpf.S3d
import io.github.memo33.scdbpf.Sc4Path
import io.github.memo33.scdbpf.StreamedEntry
import io.github.memo33.scdbpf.Tgi as ScTgi
import io.github.memo33.scdbpf.compat.ExceptionHandler
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import scala.collection.immutable.Map
import scala.jdk.javaapi.CollectionConverters
import java.io.File
import java.nio.charset.StandardCharsets

internal class ScdbpfEntryExplainer {
    private val handler: ExceptionHandler = io.github.memo33.scdbpf.`package`.strategy().throwExceptions()

    fun explainEntry(request: ExplainEntryRequest): ExplainEntryResult {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)
        val tgi = tgiToDomain(entry.tgi())
        val kind = kindForType(tgi.type)
        return when (kind) {
            KnownEntryKind.EXEMPLAR, KnownEntryKind.COHORT -> explainExemplarLike(request.path, entry, kind)
            KnownEntryKind.LTEXT -> explainLText(request.path, entry, tgi)
            KnownEntryKind.SC4PATHS -> explainSc4Paths(request.path, entry, tgi)
            KnownEntryKind.S3D -> explainS3d(request.path, entry, tgi)
            KnownEntryKind.FSH -> explainFsh(request.path, entry, tgi)
            KnownEntryKind.PNG -> explainNativeImage(request.path, entry, tgi)
            else -> explainRaw(request.path, entry, tgi, kind)
        }
    }

    private fun explainExemplarLike(path: String, entry: StreamedEntry, kind: KnownEntryKind): ExplainEntryResult {
        val tgi = tgiToDomain(entry.tgi())
        val exemplar = decodeExemplarEntry(entry).content()
        val properties = decodeProperties(exemplar)
        val name = propertyText(properties, 0x20)
        val exemplarType = propertyValues(properties, 0x10).firstOrNull()?.jsonPrimitive?.longOrNull?.let(::exemplarTypeLabel)
        val objectClass = objectClassFor(exemplarType, properties.keys)
        val parent = exemplar.parent().takeUnless(::isBlankTgi)?.let(::tgiToDomain)
        val resourceKeys = resourceKeys(properties).take(12)
        val transitRows = transitSwitchRows(properties[0xE90E25A1]).take(12)
        val lotObjectCount = properties.keys.count { it in 0x88EDC900L..0x88EDCDFFL }

        val fields = buildList {
            add(ExplanationField("name", name ?: "(unnamed)"))
            add(ExplanationField("objectClass", objectClass))
            exemplarType?.let { add(ExplanationField("exemplarType", it)) }
            add(ExplanationField("propertyCount", properties.size.toString()))
            if (lotObjectCount > 0) add(ExplanationField("lotObjectCount", lotObjectCount.toString()))
            if (transitRows.isNotEmpty()) add(ExplanationField("transitSwitchRows", transitRows.joinToString("; ")))
        }
        val relationships = buildList {
            parent?.let {
                add(ExplanationRelationship("parentCohort", it, "Parent cohort", null))
            }
            resourceKeys.forEach {
                add(ExplanationRelationship("resourceKey", it, kindForType(it.type).name, null))
            }
        }
        val warnings = buildList {
            if (parent != null) add("Parent cohort resolution is same-package only in read_exemplar/read_cohort; use resolveParent there for details.")
            if (resourceKeys.size == 12) add("Resource key list truncated at 12 entries.")
            if (transitRows.size == 12) add("Transit switch row list truncated at 12 rows.")
        }
        val summary = buildString {
            append(kind.name.lowercase().replaceFirstChar(Char::uppercase))
            append(" ")
            append(name ?: formatTgi(tgi))
            append(" is a ")
            append(objectClass)
            if (transitRows.isNotEmpty()) append(" with transit-switch properties")
            append(".")
        }
        return ExplainEntryResult(
            packagePath = File(path).absolutePath,
            tgi = tgi,
            kind = kind,
            summary = summary,
            importantFields = fields,
            relationships = relationships,
            warnings = warnings,
            suggestedNextTools = listOf("read_exemplar", "read_cohort", "inspect_package"),
        )
    }

    private fun explainLText(path: String, entry: StreamedEntry, tgi: Tgi): ExplainEntryResult {
        val ltext = decodeLTextEntry(entry).content().text()
        return ExplainEntryResult(
            packagePath = File(path).absolutePath,
            tgi = tgi,
            kind = KnownEntryKind.LTEXT,
            summary = "LTEXT resource containing ${ltext.length} characters.",
            importantFields = listOf(
                ExplanationField("length", ltext.length.toString()),
                ExplanationField("preview", ltext.take(160)),
            ),
            relationships = emptyList(),
            warnings = emptyList(),
            suggestedNextTools = listOf("read_ltext"),
        )
    }

    private fun explainSc4Paths(path: String, entry: StreamedEntry, tgi: Tgi): ExplainEntryResult {
        val sc4Path = decodeSc4PathEntry(entry).content()
        val pathCount = CollectionConverters.asJava(sc4Path.paths()).size
        val stopPathCount = CollectionConverters.asJava(sc4Path.stopPaths()).size
        return ExplainEntryResult(
            packagePath = File(path).absolutePath,
            tgi = tgi,
            kind = KnownEntryKind.SC4PATHS,
            summary = "SC4PATHS resource with $pathCount paths and $stopPathCount stop paths.",
            importantFields = listOf(
                ExplanationField("pathCount", pathCount.toString()),
                ExplanationField("stopPathCount", stopPathCount.toString()),
                ExplanationField("terrainVariance", sc4Path.terrainVariance().toString()),
            ),
            relationships = emptyList(),
            warnings = emptyList(),
            suggestedNextTools = listOf("read_sc4paths"),
        )
    }

    private fun explainS3d(path: String, entry: StreamedEntry, tgi: Tgi): ExplainEntryResult {
        val s3d = decodeS3dEntry(entry).content()
        val vertCount = CollectionConverters.asJava(s3d.vert()).sumOf { CollectionConverters.asJava(it).size }
        val materialCount = CollectionConverters.asJava(s3d.mats()).sumOf { CollectionConverters.asJava(it.materials()).size }
        return ExplainEntryResult(
            packagePath = File(path).absolutePath,
            tgi = tgi,
            kind = KnownEntryKind.S3D,
            summary = "S3D model resource with $vertCount vertices and $materialCount materials.",
            importantFields = listOf(
                ExplanationField("vertexCount", vertCount.toString()),
                ExplanationField("materialCount", materialCount.toString()),
                ExplanationField("animationFrames", s3d.anim().numFrames().toString()),
            ),
            relationships = emptyList(),
            warnings = emptyList(),
            suggestedNextTools = listOf("read_s3d"),
        )
    }

    private fun explainFsh(path: String, entry: StreamedEntry, tgi: Tgi): ExplainEntryResult {
        val fsh = decodeFshEntry(entry).content()
        val elements = CollectionConverters.asJava(fsh.elements())
        val imageCount = elements.sumOf { element -> CollectionConverters.asJava(element.images()).toList().size }
        return ExplainEntryResult(
            packagePath = File(path).absolutePath,
            tgi = tgi,
            kind = KnownEntryKind.FSH,
            summary = "FSH texture resource with ${elements.size} elements and $imageCount images.",
            importantFields = listOf(
                ExplanationField("dirId", fsh.dirId().toString()),
                ExplanationField("elementCount", elements.size.toString()),
                ExplanationField("imageCount", imageCount.toString()),
            ),
            relationships = emptyList(),
            warnings = emptyList(),
            suggestedNextTools = listOf("read_fsh", "read_image_entry"),
        )
    }

    private fun explainNativeImage(path: String, entry: StreamedEntry, tgi: Tgi): ExplainEntryResult =
        ExplainEntryResult(
            packagePath = File(path).absolutePath,
            tgi = tgi,
            kind = KnownEntryKind.PNG,
            summary = "Native PNG image entry.",
            importantFields = listOf(ExplanationField("size", entry.size().toString())),
            relationships = emptyList(),
            warnings = emptyList(),
            suggestedNextTools = listOf("read_image_entry"),
        )

    private fun explainRaw(path: String, entry: StreamedEntry, tgi: Tgi, kind: KnownEntryKind): ExplainEntryResult {
        val raw = entry.toRawEntry(handler) as RawEntry
        val bytes = io.github.memo33.scdbpf.compat.Input.slurpBytes(raw.input(), handler) as ByteArray
        val utf8Preview = bytes.copyOf(minOf(bytes.size, 160))
            .toString(StandardCharsets.UTF_8)
            .takeIf { text -> text.all { it == '\n' || it == '\r' || it == '\t' || !it.isISOControl() } }
        return ExplainEntryResult(
            packagePath = File(path).absolutePath,
            tgi = tgi,
            kind = kind,
            summary = "${kind.name} entry with ${bytes.size} bytes; no specialized semantic explainer is available yet.",
            importantFields = buildList {
                add(ExplanationField("size", bytes.size.toString()))
                add(ExplanationField("compressed", raw.compressed().toString()))
                utf8Preview?.let { add(ExplanationField("utf8Preview", it)) }
            },
            relationships = emptyList(),
            warnings = if (kind == KnownEntryKind.UNKNOWN) listOf("Unknown DBPF type; use read_raw_entry for byte-level inspection.") else emptyList(),
            suggestedNextTools = listOf("read_raw_entry"),
        )
    }

    private fun readPackage(path: String): DbpfFile {
        val file = requireDbpfPackageFile(path)
        return try {
            DbpfFile.read(file, handler) as DbpfFile
        } catch (exception: Exception) {
            throw PackageError("Failed to read DBPF package: ${file.absolutePath}", exception)
        }
    }

    private fun findEntry(dbpf: DbpfFile, tgi: Tgi): StreamedEntry =
        CollectionConverters.asJava(dbpf.entries())
            .map { it as StreamedEntry }
            .firstOrNull { tgiToDomain(it.tgi()) == tgi }
            ?: throw PackageError("Entry not found for TGI $tgi")

    @Suppress("UNCHECKED_CAST")
    private fun decodeExemplarEntry(entry: StreamedEntry): BufferedEntry<Exemplar> {
        val buffered = entry.toBufferedEntry(handler) as BufferedEntry<DbpfType>
        return buffered.convert(handler, Exemplar.converter()) as BufferedEntry<Exemplar>
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeLTextEntry(entry: StreamedEntry): BufferedEntry<LText> {
        val buffered = entry.toBufferedEntry(handler) as BufferedEntry<DbpfType>
        return buffered.convert(handler, LText.contentConverter()) as BufferedEntry<LText>
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeSc4PathEntry(entry: StreamedEntry): BufferedEntry<Sc4Path> {
        val buffered = entry.toBufferedEntry(handler) as BufferedEntry<DbpfType>
        return buffered.convert(handler, Sc4Path.contentConverter()) as BufferedEntry<Sc4Path>
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeS3dEntry(entry: StreamedEntry): BufferedEntry<S3d> {
        val buffered = entry.toBufferedEntry(handler) as BufferedEntry<DbpfType>
        return buffered.convert(handler, S3d.contentConverter()) as BufferedEntry<S3d>
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeFshEntry(entry: StreamedEntry): BufferedEntry<Fsh> {
        val buffered = entry.toBufferedEntry(handler) as BufferedEntry<DbpfType>
        return buffered.convert(handler, Fsh.contentConverter()) as BufferedEntry<Fsh>
    }

    @Suppress("UNCHECKED_CAST")
    private fun scalaMapEntries(properties: Map<UInt, DbpfProperty.PropertyList<*>>): List<Pair<UInt, DbpfProperty.PropertyList<*>>> =
        CollectionConverters.asJava(properties).entries.map { it.key to it.value }

    private fun decodeProperties(exemplar: Exemplar): kotlin.collections.Map<Long, List<JsonElement>> =
        scalaMapEntries(exemplar.properties()).associate { (id, propertyList) ->
            id.toLong() to propertyValues(propertyList)
        }

    private fun propertyValues(propertyList: DbpfProperty.PropertyList<*>): List<JsonElement> = when (propertyList) {
        is DbpfProperty.Single<*> -> listOf(valueToJson(propertyList.value()))
        is DbpfProperty.Multi<*> -> CollectionConverters.asJava(propertyList.values()).map(::valueToJson)
        else -> listOf(JsonPrimitive(propertyList.toString()))
    }

    private fun valueToJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value.toLong())
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value.toDouble())
        is Double -> JsonPrimitive(value)
        is UInt -> JsonPrimitive(value.toLong())
        is UShort -> JsonPrimitive(value.toInt())
        is ScTgi -> JsonArray(
            listOf(
                JsonPrimitive(unsignedInt(value.tid())),
                JsonPrimitive(unsignedInt(value.gid())),
                JsonPrimitive(unsignedInt(value.iid())),
            )
        )
        else -> JsonPrimitive(value.toString())
    }

    private fun propertyText(properties: kotlin.collections.Map<Long, List<JsonElement>>, id: Long): String? =
        propertyValues(properties, id).firstOrNull()?.jsonPrimitive?.contentOrNull

    private fun propertyValues(properties: kotlin.collections.Map<Long, List<JsonElement>>, id: Long): List<JsonElement> =
        properties[id] ?: emptyList()

    private fun resourceKeys(properties: kotlin.collections.Map<Long, List<JsonElement>>): List<Tgi> =
        listOf(0x27812820L, 0x27812821L, 0x27812822L, 0x2781282AL, 0x27812832L, 0x27812840L, 0x27812841L, 0x27812843L, 0x27812844L, 0x27812845L)
            .flatMap { id ->
                propertyValues(properties, id)
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
                    .chunked(3)
                    .filter { it.size == 3 }
                    .map { Tgi(it[0], it[1], it[2]) }
            }

    private fun transitSwitchRows(values: List<JsonElement>?): List<String> {
        val bytes = values.orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.and(0xFF) }
        return bytes.chunked(4)
            .filter { it.size == 4 }
            .map { (art, edges, from, to) ->
                "${artLabel(art)} ${edgeLabel(edges)} ${travelLabel(from)} -> ${travelLabel(to)}"
            }
    }

    private fun objectClassFor(exemplarType: String?, propertyIds: Set<Long>): String {
        if (propertyIds.any { it in 0x88EDC900L..0x88EDCDFFL }) return "Lot"
        if (propertyIds.any { it in setOf(0xE90E25A1L, 0xE90E25A2L, 0xE90E25A3L) }) return "Transit-enabled Building"
        return exemplarType ?: "Exemplar"
    }

    private fun exemplarTypeLabel(value: Long): String? = when (value) {
        0x00000001L -> "Cohort"
        0x00000002L -> "Exemplar"
        0x0000000AL -> "Lot Configuration"
        0x0000000BL -> "Network"
        0x0000001EL -> "Prop"
        0x00000021L -> "Network Lot (T21)"
        else -> "Unknown"
    }

    private fun artLabel(value: Int): String = when (value) {
        0x81 -> "Outside->Inside"
        0x82 -> "Inside->Outside"
        else -> "0x%02X".format(value)
    }

    private fun travelLabel(value: Int): String = when (value) {
        0 -> "Walk"
        1 -> "Car"
        2 -> "Bus"
        3 -> "Train"
        4 -> "FreightTruck"
        5 -> "FreightTrain"
        6 -> "Subway"
        7 -> "ElTrain"
        8 -> "Monorail"
        else -> "0x%02X".format(value)
    }

    private fun edgeLabel(value: Int): String {
        val mask = value and 0xF0
        if (mask == 0) return "No edges"
        if (mask == 0xF0) return "All sides"
        return buildList {
            if (mask and 0x40 != 0) add("N")
            if (mask and 0x80 != 0) add("W")
            if (mask and 0x10 != 0) add("S")
            if (mask and 0x20 != 0) add("E")
        }.joinToString("+")
    }

    private fun tgiToDomain(tgi: ScTgi): Tgi = Tgi(
        type = unsignedInt(tgi.tid()),
        group = unsignedInt(tgi.gid()),
        instance = unsignedInt(tgi.iid()),
    )

    private fun isBlankTgi(tgi: ScTgi): Boolean =
        unsignedInt(tgi.tid()) == 0L && unsignedInt(tgi.gid()) == 0L && unsignedInt(tgi.iid()) == 0L

    private fun unsignedInt(value: Any): Long = (value as Number).toLong() and 0xFFFF_FFFFL

    private fun formatTgi(tgi: Tgi): String =
        "${formatHex32(tgi.type)}-${formatHex32(tgi.group)}-${formatHex32(tgi.instance)}"
}
