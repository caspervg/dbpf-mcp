package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.DbpfException
import com.github.caspervg.dbpfmcp.core.DecodeError
import com.github.caspervg.dbpfmcp.core.ExplainEntryRequest
import com.github.caspervg.dbpfmcp.core.ExplainEntryResult
import com.github.caspervg.dbpfmcp.core.ExplanationField
import com.github.caspervg.dbpfmcp.core.ExplanationRelationship
import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.semantics.exemplarTypeLabel
import com.github.caspervg.dbpfmcp.semantics.kindForType
import com.github.caspervg.dbpfmcp.semantics.objectClassFor
import com.github.caspervg.dbpfmcp.semantics.resourceKeyPropertyIds
import com.github.caspervg.dbpfmcp.semantics.resourceKeysFrom
import io.github.memo33.scdbpf.Exemplar
import io.github.memo33.scdbpf.RawEntry
import io.github.memo33.scdbpf.StreamedEntry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import scala.collection.immutable.Map
import scala.jdk.javaapi.CollectionConverters
import java.io.File
import java.nio.charset.StandardCharsets

internal class ScdbpfEntryExplainer {

    fun explainEntry(request: ExplainEntryRequest): ExplainEntryResult {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)
        val tgi = tgiToDomain(entry.tgi())
        val kind = kindForType(tgi.type)
        // Every branch below decodes entry content. Without this, a corrupt entry throws a raw
        // scdbpf exception that reaches the server as an untyped, sometimes message-less error.
        return try {
            when (kind) {
                KnownEntryKind.EXEMPLAR, KnownEntryKind.COHORT -> explainExemplarLike(request.path, entry, kind)
                KnownEntryKind.LTEXT -> explainLText(request.path, entry, tgi)
                KnownEntryKind.SC4PATHS -> explainSc4Paths(request.path, entry, tgi)
                KnownEntryKind.S3D -> explainS3d(request.path, entry, tgi)
                KnownEntryKind.FSH -> explainFsh(request.path, entry, tgi)
                KnownEntryKind.PNG -> explainNativeImage(request.path, entry, tgi)
                else -> explainRaw(request.path, entry, tgi, kind)
            }
        } catch (exception: DbpfException) {
            throw exception
        } catch (exception: Exception) {
            throw DecodeError("Failed to explain ${kind.name} entry ${formatTgi(tgi)}", exception)
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
        val materialCount = CollectionConverters.asJava(s3d.mats()).sumOf {
            CollectionConverters.asJava(it.materials()).size
        }
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
        val raw = entry.toRawEntry(dbpfHandler) as RawEntry
        val bytes = io.github.memo33.scdbpf.compat.Input.slurpBytes(raw.input(), dbpfHandler) as ByteArray
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

    private fun decodeProperties(exemplar: Exemplar): kotlin.collections.Map<Long, List<JsonElement>> =
        scalaMapEntries(exemplar.properties()).associate { (id, propertyList) ->
            id.toLong() to propertyValues(propertyList)
        }

    private fun propertyText(properties: kotlin.collections.Map<Long, List<JsonElement>>, id: Long): String? =
        propertyValues(properties, id).firstOrNull()?.jsonPrimitive?.contentOrNull

    private fun propertyValues(properties: kotlin.collections.Map<Long, List<JsonElement>>, id: Long): List<JsonElement> =
        properties[id] ?: emptyList()

    private fun resourceKeys(properties: kotlin.collections.Map<Long, List<JsonElement>>): List<Tgi> =
        resourceKeyPropertyIds.flatMap { id -> resourceKeysFrom(propertyValues(properties, id)) }

    private fun transitSwitchRows(values: List<JsonElement>?): List<String> {
        val bytes = values.orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.and(0xFF) }
        return bytes.chunked(4)
            .filter { it.size == 4 }
            .map { (art, edges, from, to) ->
                "${artLabel(art)} ${edgeLabel(edges)} ${travelLabel(from)} -> ${travelLabel(to)}"
            }
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
}
