package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.CohortModel
import com.github.caspervg.dbpfmcp.core.DecodePropertyValueRequest
import com.github.caspervg.dbpfmcp.core.DecodedPropertyModel
import com.github.caspervg.dbpfmcp.core.DescribePropertyRequest
import com.github.caspervg.dbpfmcp.core.DbpfService
import com.github.caspervg.dbpfmcp.core.DecodeError
import com.github.caspervg.dbpfmcp.core.EntrySummary
import com.github.caspervg.dbpfmcp.core.ExportedFileModel
import com.github.caspervg.dbpfmcp.core.ExemplarModel
import com.github.caspervg.dbpfmcp.core.ExemplarProperty
import com.github.caspervg.dbpfmcp.core.ExportCohortTextRequest
import com.github.caspervg.dbpfmcp.core.ExportExemplarTextRequest
import com.github.caspervg.dbpfmcp.core.ExportFshPngRequest
import com.github.caspervg.dbpfmcp.core.ExportSC4PathsJsonRequest
import com.github.caspervg.dbpfmcp.core.ExportSC4PathsTextRequest
import com.github.caspervg.dbpfmcp.core.ExplainEntryRequest
import com.github.caspervg.dbpfmcp.core.ExplainEntryResult
import com.github.caspervg.dbpfmcp.core.FshElementSummary
import com.github.caspervg.dbpfmcp.core.FshImageSummary
import com.github.caspervg.dbpfmcp.core.FshModel
import com.github.caspervg.dbpfmcp.core.ImageEntryModel
import com.github.caspervg.dbpfmcp.core.IndexPluginsRequest
import com.github.caspervg.dbpfmcp.core.IndexPluginsResult
import com.github.caspervg.dbpfmcp.core.IndexStatusRequest
import com.github.caspervg.dbpfmcp.core.IndexStatusResult
import com.github.caspervg.dbpfmcp.core.InspectPackageRequest
import com.github.caspervg.dbpfmcp.core.InspectPackageResult
import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.KeyCfgModel
import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import com.github.caspervg.dbpfmcp.core.LTextModel
import com.github.caspervg.dbpfmcp.core.ListEntriesRequest
import com.github.caspervg.dbpfmcp.core.ListEntriesResult
import com.github.caspervg.dbpfmcp.core.NotableEntry
import com.github.caspervg.dbpfmcp.core.PackageKindSummary
import com.github.caspervg.dbpfmcp.core.PackageSummary
import com.github.caspervg.dbpfmcp.core.PackageError
import com.github.caspervg.dbpfmcp.core.ParentChainItem
import com.github.caspervg.dbpfmcp.core.PropertyDescription
import com.github.caspervg.dbpfmcp.core.ReadCohortRequest
import com.github.caspervg.dbpfmcp.core.ReadCohortTextRequest
import com.github.caspervg.dbpfmcp.core.ReadExemplarRequest
import com.github.caspervg.dbpfmcp.core.ReadExemplarTextRequest
import com.github.caspervg.dbpfmcp.core.ReadFshRequest
import com.github.caspervg.dbpfmcp.core.ReadImageEntryRequest
import com.github.caspervg.dbpfmcp.core.ReadKeyCfgRequest
import com.github.caspervg.dbpfmcp.core.ReadLTextRequest
import com.github.caspervg.dbpfmcp.core.ReadS3dRequest
import com.github.caspervg.dbpfmcp.core.ReadSC4PathsRequest
import com.github.caspervg.dbpfmcp.core.ReadTabBinaryRequest
import com.github.caspervg.dbpfmcp.core.RawEntryModel
import com.github.caspervg.dbpfmcp.core.ReadRawEntryRequest
import com.github.caspervg.dbpfmcp.core.SC4PathCoordinate
import com.github.caspervg.dbpfmcp.core.SC4PathRecord
import com.github.caspervg.dbpfmcp.core.SC4PathsModel
import com.github.caspervg.dbpfmcp.core.SC4StopPathRecord
import com.github.caspervg.dbpfmcp.core.Sc4ObjectHint
import com.github.caspervg.dbpfmcp.core.SearchIndexRequest
import com.github.caspervg.dbpfmcp.core.SearchIndexResult
import com.github.caspervg.dbpfmcp.core.S3dAnimGroupSummary
import com.github.caspervg.dbpfmcp.core.S3dIndxGroupSummary
import com.github.caspervg.dbpfmcp.core.S3dMatsGroupSummary
import com.github.caspervg.dbpfmcp.core.S3dMaterialSummary
import com.github.caspervg.dbpfmcp.core.S3dModel
import com.github.caspervg.dbpfmcp.core.S3dPrimGroupSummary
import com.github.caspervg.dbpfmcp.core.S3dPrimSummary
import com.github.caspervg.dbpfmcp.core.S3dPropSummary
import com.github.caspervg.dbpfmcp.core.S3dRegpSummary
import com.github.caspervg.dbpfmcp.core.S3dVertGroupSummary
import com.github.caspervg.dbpfmcp.core.SummarizePackageRequest
import com.github.caspervg.dbpfmcp.core.TabBinaryModel
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.core.TextEntryModel
import com.github.caspervg.dbpfmcp.semantics.formatHex32
import com.github.caspervg.dbpfmcp.semantics.SC4TypeIds
import com.github.caspervg.dbpfmcp.semantics.canonicalPropertyType
import com.github.caspervg.dbpfmcp.semantics.decodePropertyValue
import com.github.caspervg.dbpfmcp.semantics.describeProperty
import com.github.caspervg.dbpfmcp.semantics.kindForType
import com.github.caspervg.dbpfmcp.semantics.maybeExemplarName
import com.github.caspervg.dbpfmcp.semantics.propertyName
import com.github.caspervg.dbpfmcp.semantics.typesAreCompatible
import io.github.memo33.passera.unsigned.UInt
import io.github.memo33.passera.unsigned.UShort
import io.github.memo33.scdbpf.BufferedEntry
import io.github.memo33.scdbpf.DbpfType
import io.github.memo33.scdbpf.DbpfFile
import io.github.memo33.scdbpf.DbpfProperty
import io.github.memo33.scdbpf.Exemplar
import io.github.memo33.scdbpf.Fsh
import io.github.memo33.scdbpf.LText
import io.github.memo33.scdbpf.RawEntry
import io.github.memo33.scdbpf.S3d
import io.github.memo33.scdbpf.Sc4Path
import io.github.memo33.scdbpf.StreamedEntry
import io.github.memo33.scdbpf.Tgi as ScTgi
import io.github.memo33.scdbpf.compat.Input
import io.github.memo33.scdbpf.compat.ExceptionHandler
import io.github.memo33.scdbpf.compat.Image
import io.github.memo33.scdbpf.compat.RGBA
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import scala.collection.immutable.Map
import scala.jdk.javaapi.CollectionConverters
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class ScdbpfAdapter : DbpfService {
    override val backendName: String = "scdbpf"

    private val handler: ExceptionHandler = io.github.memo33.scdbpf.`package`.strategy().throwExceptions()
    private val pluginIndexer = ScdbpfPluginIndexer()
    private val entryExplainer = ScdbpfEntryExplainer()
    private val json = Json { prettyPrint = true }

    private val resourceKeyPropertyIds = setOf(
        0x27812820L,
        0x27812821L,
        0x27812822L,
        0x2781282AL,
        0x27812832L,
        0x27812840L,
        0x27812841L,
        0x27812843L,
        0x27812844L,
        0x27812845L,
    )

    override fun listEntries(request: ListEntriesRequest): ListEntriesResult {
        validatePaging(request)
        val dbpf = readPackage(request.path)
        val entries = CollectionConverters.asJava(dbpf.entries()).map { entry ->
            entry as StreamedEntry
        }
        val limit = request.limit
        val offset = request.offset ?: 0
        val labelContains = request.labelContains

        val filtered = entries.asSequence()
            .map { entry ->
                val tgi = tgiToDomain(entry.tgi())
                val rawEntry = entry.toRawEntry(handler) as RawEntry
                EntrySummary(
                    tgi = tgi,
                    kind = kindForType(tgi.type),
                    size = entry.size().toLong(),
                    compressed = rawEntry.compressed(),
                    label = entry.tgi().label().takeIf(String::isNotBlank),
                )
            }
            .filter { request.typeFilter == null || it.tgi.type == request.typeFilter }
            .filter { request.groupFilter == null || it.tgi.group == request.groupFilter }
            .filter { request.kindFilter == null || it.kind == request.kindFilter }
            .filter { labelContains == null || (it.label?.contains(labelContains, ignoreCase = true) == true) }
            .drop(offset)
            .let { sequence -> if (limit != null) sequence.take(limit) else sequence }
            .toList()

        return ListEntriesResult(
            packagePath = File(request.path).absolutePath,
            entryCount = entries.size,
            entries = filtered,
        )
    }

    override fun summarizePackage(request: SummarizePackageRequest): PackageSummary {
        val dbpf = readPackage(request.path)
        val summaries = packageEntrySummaries(dbpf)
        val countsByKind = summaries.groupingBy { it.kind }.eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { PackageKindSummary(it.key, it.value) }
        val compressedCount = summaries.count { it.compressed == true }
        val topLabels = summaries.asSequence()
            .mapNotNull { it.label }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { "${it.key} (${it.value})" }

        return PackageSummary(
            packagePath = File(request.path).absolutePath,
            entryCount = summaries.size,
            compressedCount = compressedCount,
            uncompressedCount = summaries.size - compressedCount,
            countsByKind = countsByKind,
            topLabels = topLabels,
        )
    }

    override fun inspectPackage(request: InspectPackageRequest): InspectPackageResult {
        validateInspectLimits(request)
        val dbpf = readPackage(request.path)
        val summaries = packageEntrySummaries(dbpf)
        val countsByKind = summaries.groupingBy { it.kind }.eachCount()
            .entries
            .sortedWith(compareByDescending<kotlin.collections.Map.Entry<KnownEntryKind, Int>> { it.value }.thenBy { it.key.name })
            .map { PackageKindSummary(it.key, it.value) }
        val compressedCount = summaries.count { it.compressed == true }
        val topLabels = summaries.asSequence()
            .mapNotNull { it.label }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { "${it.key} (${it.value})" }
        val maxNotableEntries = request.maxNotableEntries ?: 20
        val maxObjectHints = request.maxObjectHints ?: 25
        val warnings = mutableListOf<String>()
        val notableEntries = summaries.asSequence()
            .filter { it.kind != KnownEntryKind.UNKNOWN || it.label != null }
            .sortedWith(compareBy<EntrySummary> { notablePriority(it.kind) }.thenBy { it.tgi.type }.thenBy { it.tgi.instance })
            .take(maxNotableEntries)
            .map {
                NotableEntry(
                    tgi = it.tgi,
                    kind = it.kind,
                    label = it.label,
                    size = it.size,
                    reason = notableReason(it),
                )
            }
            .toList()
        val objectHints = CollectionConverters.asJava(dbpf.entries())
            .asSequence()
            .map { it as StreamedEntry }
            .filter { tgiToDomain(it.tgi()).type in setOf(SC4TypeIds.EXEMPLAR, SC4TypeIds.COHORT) }
            .mapNotNull { entry -> inspectExemplarLikeEntry(entry, warnings) }
            .take(maxObjectHints)
            .toList()

        if (notableEntries.size == maxNotableEntries) {
            warnings += "notableEntries truncated at $maxNotableEntries; use list_entries with filters for full results."
        }
        if (objectHints.size == maxObjectHints) {
            warnings += "sc4ObjectHints truncated at $maxObjectHints; use read_exemplar/read_cohort for specific entries."
        }

        return InspectPackageResult(
            packagePath = File(request.path).absolutePath,
            entryCount = summaries.size,
            compressedCount = compressedCount,
            uncompressedCount = summaries.size - compressedCount,
            countsByKind = countsByKind,
            topLabels = topLabels,
            notableEntries = notableEntries,
            sc4ObjectHints = objectHints,
            warnings = warnings.distinct(),
            recommendedNextTools = listOf(
                "list_entries",
                "read_exemplar",
                "read_cohort",
                "read_image_entry",
                "read_raw_entry",
            ),
        )
    }

    override fun indexPlugins(request: IndexPluginsRequest): IndexPluginsResult =
        pluginIndexer.indexPlugins(request)

    override fun indexStatus(request: IndexStatusRequest): IndexStatusResult =
        pluginIndexer.indexStatus(request)

    override fun searchIndex(request: SearchIndexRequest): SearchIndexResult =
        pluginIndexer.searchIndex(request)

    override fun explainEntry(request: ExplainEntryRequest): ExplainEntryResult =
        entryExplainer.explainEntry(request)

    override fun readExemplar(request: ReadExemplarRequest): ExemplarModel {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)

        if (tgiToDomain(entry.tgi()).type != SC4TypeIds.EXEMPLAR) {
            throw InputError("Requested entry is not an exemplar")
        }

        val exemplarEntry = try {
            decodeExemplarEntry(entry)
        } catch (exception: Exception) {
            throw DecodeError("Failed to decode exemplar ${request.tgi}", exception)
        }

        val exemplar = exemplarEntry.content()
        val properties = decodeProperties(exemplar)
        val exemplarName = properties.firstOrNull { maybeExemplarName(it.id) }
            ?.values
            ?.firstOrNull()
            ?.jsonPrimitive
            ?.contentOrNull

        val parent = exemplar.parent()
        return ExemplarModel(
            tgi = request.tgi,
            parentCohort = if (isBlankTgi(parent)) null else tgiToDomain(parent),
            exemplarName = exemplarName,
            properties = properties,
            parentChain = if (request.resolveParent) {
                resolveParentChain(dbpf, parent, request.path, request.rootPath)
            } else {
                emptyList()
            },
        )
    }

    override fun readCohort(request: ReadCohortRequest): CohortModel {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)

        if (tgiToDomain(entry.tgi()).type != SC4TypeIds.COHORT) {
            throw InputError("Requested entry is not a cohort")
        }

        val cohortEntry = try {
            decodeExemplarEntry(entry)
        } catch (exception: Exception) {
            throw DecodeError("Failed to decode cohort ${request.tgi}", exception)
        }

        val cohort = cohortEntry.content()
        if (!cohort.isCohort()) {
            throw DecodeError("Requested entry was decoded but not marked as a cohort")
        }
        val properties = decodeProperties(cohort)
        val cohortName = properties.firstOrNull { maybeExemplarName(it.id) }
            ?.values
            ?.firstOrNull()
            ?.jsonPrimitive
            ?.contentOrNull
        val parent = cohort.parent()
        return CohortModel(
            tgi = request.tgi,
            parentCohort = if (isBlankTgi(parent)) null else tgiToDomain(parent),
            cohortName = cohortName,
            properties = properties,
            parentChain = if (request.resolveParent) {
                resolveParentChain(dbpf, parent, request.path, request.rootPath)
            } else {
                emptyList()
            },
        )
    }

    override fun readExemplarText(request: ReadExemplarTextRequest): TextEntryModel {
        val exemplar = decodeExemplarForText(request.path, request.tgi, expectedType = SC4TypeIds.EXEMPLAR)
        return TextEntryModel(
            tgi = request.tgi,
            kind = KnownEntryKind.EXEMPLAR,
            format = TextExemplarRenderer.FORMAT,
            propertyCount = CollectionConverters.asJava(exemplar.properties()).size,
            text = TextExemplarRenderer.render(exemplar),
        )
    }

    override fun readCohortText(request: ReadCohortTextRequest): TextEntryModel {
        val cohort = decodeExemplarForText(request.path, request.tgi, expectedType = SC4TypeIds.COHORT)
        if (!cohort.isCohort()) {
            throw DecodeError("Requested entry was decoded but not marked as a cohort")
        }
        return TextEntryModel(
            tgi = request.tgi,
            kind = KnownEntryKind.COHORT,
            format = TextExemplarRenderer.FORMAT,
            propertyCount = CollectionConverters.asJava(cohort.properties()).size,
            text = TextExemplarRenderer.render(cohort),
        )
    }

    override fun exportExemplarText(request: ExportExemplarTextRequest): ExportedFileModel {
        val model = readExemplarText(ReadExemplarTextRequest(path = request.path, tgi = request.tgi))
        return writeTextExport(model, request.outputPath)
    }

    override fun exportCohortText(request: ExportCohortTextRequest): ExportedFileModel {
        val model = readCohortText(ReadCohortTextRequest(path = request.path, tgi = request.tgi))
        return writeTextExport(model, request.outputPath)
    }

    override fun readLText(request: ReadLTextRequest): LTextModel {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)

        if (tgiToDomain(entry.tgi()).type != SC4TypeIds.LTEXT) {
            throw InputError("Requested entry is not an LTEXT entry")
        }

        val ltextEntry = try {
            decodeLTextEntry(entry)
        } catch (exception: Exception) {
            throw DecodeError("Failed to decode LTEXT ${request.tgi}", exception)
        }

        val text = ltextEntry.content().text()
        return LTextModel(
            tgi = request.tgi,
            text = text,
            length = text.length,
        )
    }

    override fun readSC4Paths(request: ReadSC4PathsRequest): SC4PathsModel {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)

        if (tgiToDomain(entry.tgi()).type != SC4TypeIds.SC4PATHS) {
            throw InputError("Requested entry is not an SC4PATHS entry")
        }

        val pathEntry = try {
            decodeSc4PathEntry(entry)
        } catch (exception: Exception) {
            throw DecodeError("Failed to decode SC4PATHS ${request.tgi}", exception)
        }

        val sc4Path = pathEntry.content()
        val paths = CollectionConverters.asJava(sc4Path.paths()).map { path ->
            SC4PathRecord(
                comment = scalaOptionToNullable(path.comment()),
                transportType = path.transportType().toString(),
                classNumber = path.classNumber(),
                entry = path.entry().toString(),
                exit = path.exit().toString(),
                junction = path.junction(),
                coords = CollectionConverters.asJava(path.coords()).map(::tupleToCoordinate),
            )
        }
        val stopPaths = CollectionConverters.asJava(sc4Path.stopPaths()).map { path ->
            SC4StopPathRecord(
                comment = scalaOptionToNullable(path.comment()),
                uk = path.uk(),
                transportType = path.transportType().toString(),
                classNumber = path.classNumber(),
                entry = path.entry().toString(),
                exit = path.exit().toString(),
                coord = tupleToCoordinate(path.coord()),
            )
        }

        return SC4PathsModel(
            tgi = request.tgi,
            terrainVariance = sc4Path.terrainVariance(),
            pathCount = paths.size,
            stopPathCount = stopPaths.size,
            textPreview = sc4Path.toString().lineSequence().take(12).joinToString("\n"),
            paths = paths,
            stopPaths = stopPaths,
        )
    }

    override fun readSC4PathsText(request: ReadSC4PathsRequest): TextEntryModel {
        val sc4Path = decodeSC4PathForExport(request.path, request.tgi)
        return TextEntryModel(
            tgi = request.tgi,
            kind = KnownEntryKind.SC4PATHS,
            format = "sc4paths-text",
            text = sc4Path.toString(),
        )
    }

    override fun exportSC4PathsText(request: ExportSC4PathsTextRequest): ExportedFileModel {
        val model = readSC4PathsText(ReadSC4PathsRequest(path = request.path, tgi = request.tgi))
        return writeTextExport(model, request.outputPath)
    }

    override fun exportSC4PathsJson(request: ExportSC4PathsJsonRequest): ExportedFileModel {
        val model = readSC4Paths(ReadSC4PathsRequest(path = request.path, tgi = request.tgi))
        val text = json.encodeToString(model)
        val outputPath = writeBytes(request.outputPath, text.toByteArray(StandardCharsets.UTF_8))
        return ExportedFileModel(
            tgi = request.tgi,
            kind = KnownEntryKind.SC4PATHS,
            format = "sc4paths-json",
            outputPath = outputPath.toAbsolutePath().toString(),
            bytesWritten = Files.size(outputPath),
        )
    }

    override fun readS3d(request: ReadS3dRequest): S3dModel {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)

        if (tgiToDomain(entry.tgi()).type != SC4TypeIds.S3D) {
            throw InputError("Requested entry is not an S3D entry")
        }

        val s3dEntry = try {
            decodeS3dEntry(entry)
        } catch (exception: Exception) {
            throw DecodeError("Failed to decode S3D ${request.tgi}", exception)
        }

        val s3d = s3dEntry.content()
        val vertGroups = CollectionConverters.asJava(s3d.vert()).mapIndexed { index, group ->
            S3dVertGroupSummary(index = index, vertexCount = CollectionConverters.asJava(group).size)
        }
        val indxGroups = CollectionConverters.asJava(s3d.indx()).mapIndexed { index, group ->
            S3dIndxGroupSummary(index = index, indexCount = CollectionConverters.asJava(group).size)
        }
        val primGroups = CollectionConverters.asJava(s3d.prim()).mapIndexed { index, group ->
            val primitives = CollectionConverters.asJava(group).map { prim ->
                S3dPrimSummary(
                    type = prim.primType().toString(),
                    firstIndex = prim.firstIndx(),
                    indexCount = prim.numIndxs(),
                )
            }
            S3dPrimGroupSummary(
                index = index,
                primitiveCount = primitives.size,
                primitives = primitives,
            )
        }
        val matsGroups = CollectionConverters.asJava(s3d.mats()).mapIndexed { index, group ->
            val materials = CollectionConverters.asJava(group.materials()).map { material ->
                S3dMaterialSummary(
                    id = formatHex32(unsignedInt(material.id())),
                    wrapU = material.wrapU().toString(),
                    wrapV = material.wrapV().toString(),
                    magFilter = material.magFilter().toString(),
                    minFilter = material.minFilter().toString(),
                    animRate = material.animRate().toInt(),
                    animMode = material.animMode().toInt(),
                    name = scalaOptionToNullable(material.name()),
                )
            }
            S3dMatsGroupSummary(
                index = index,
                flags = CollectionConverters.asJava(group.flags()).map { it.toString() }.sorted(),
                alphaFunc = group.alphaFunc().toString(),
                depthFunc = group.depthFunc().toString(),
                sourceBlend = group.sourceBlend().toString(),
                destBlend = group.destBlend().toString(),
                alphaThreshold = group.alphaThreshold().toInt(),
                materialCount = materials.size,
                materials = materials,
            )
        }
        val animGroups = CollectionConverters.asJava(s3d.anim().groups()).mapIndexed { index, group ->
            S3dAnimGroupSummary(
                index = index,
                name = scalaOptionToNullable(group.name()),
                flags = group.flags(),
                frameBlockCount = CollectionConverters.asJava(group.vertBlock()).size,
            )
        }
        val props = CollectionConverters.asJava(s3d.prop()).map { prop ->
            S3dPropSummary(
                meshIndex = prop.meshIndex().toInt(),
                frameIndex = prop.frameIndex().toInt(),
                assignmentType = prop.assignmentType(),
                assignedValue = prop.assignedValue(),
            )
        }
        val regpGroups = CollectionConverters.asJava(s3d.regp()).map { group ->
            S3dRegpSummary(
                name = group.name(),
                transformCount = CollectionConverters.asJava(group.translations()).size,
            )
        }

        return S3dModel(
            tgi = request.tgi,
            vertGroupCount = vertGroups.size,
            indxGroupCount = indxGroups.size,
            primGroupCount = primGroups.size,
            matsGroupCount = matsGroups.size,
            propCount = props.size,
            regpCount = regpGroups.size,
            totalVertices = vertGroups.sumOf { it.vertexCount },
            totalIndices = indxGroups.sumOf { it.indexCount },
            totalPrimitives = primGroups.sumOf { it.primitiveCount },
            animFrameCount = s3d.anim().numFrames().toInt(),
            animFrameRate = s3d.anim().frameRate().toInt(),
            animPlayMode = s3d.anim().playMode().toString(),
            animDisplacement = s3d.anim().displacement(),
            vertGroups = vertGroups,
            indxGroups = indxGroups,
            primGroups = primGroups,
            matsGroups = matsGroups,
            animGroups = animGroups,
            props = props,
            regpGroups = regpGroups,
        )
    }

    override fun readFsh(request: ReadFshRequest): FshModel {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)

        if (tgiToDomain(entry.tgi()).type != SC4TypeIds.FSH) {
            throw InputError("Requested entry is not an FSH entry")
        }

        val fshEntry = try {
            decodeFshEntry(entry)
        } catch (exception: Exception) {
            throw DecodeError("Failed to decode FSH ${request.tgi}", exception)
        }

        val fsh = fshEntry.content()
        val elements = CollectionConverters.asJava(fsh.elements()).mapIndexed { index, element ->
            val images = CollectionConverters.asJava(element.images()).mapIndexed { imageIndex, image ->
                FshImageSummary(
                    index = imageIndex,
                    width = image.width(),
                    height = image.height(),
                    mipLevel = imageIndex,
                )
            }
            FshElementSummary(
                index = index,
                format = element.format().toString(),
                label = scalaOptionToNullable(element.label()),
                imageCount = images.size,
                images = images,
            )
        }
        val preview = if (request.previewElementIndex != null || request.previewImageIndex != null) {
            renderFshImage(
                tgi = request.tgi,
                fsh = fsh,
                elementIndex = request.previewElementIndex ?: 0,
                imageIndex = request.previewImageIndex ?: 0,
            )
        } else {
            null
        }

        return FshModel(
            tgi = request.tgi,
            dirId = fsh.dirId().toString(),
            elementCount = elements.size,
            imageCount = elements.sumOf { it.imageCount },
            elements = elements,
            preview = preview,
        )
    }

    override fun readImageEntry(request: ReadImageEntryRequest): ImageEntryModel {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)
        val kind = kindForType(request.tgi.type)
        return when (request.tgi.type) {
            SC4TypeIds.PNG -> readNativePngEntry(entry, request.tgi)
            SC4TypeIds.FSH -> {
                val fshEntry = try {
                    decodeFshEntry(entry)
                } catch (exception: Exception) {
                    throw DecodeError("Failed to decode FSH ${request.tgi}", exception)
                }
                renderFshImage(
                    tgi = request.tgi,
                    fsh = fshEntry.content(),
                    elementIndex = request.elementIndex ?: 0,
                    imageIndex = request.imageIndex ?: 0,
                )
            }
            else -> throw InputError("Requested entry is not an image entry: ${kind.name}")
        }
    }

    override fun exportFshPng(request: ExportFshPngRequest): ExportedFileModel {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)
        if (request.tgi.type != SC4TypeIds.FSH) {
            throw InputError("Requested entry is not an FSH entry")
        }
        val fshEntry = try {
            decodeFshEntry(entry)
        } catch (exception: Exception) {
            throw DecodeError("Failed to decode FSH ${request.tgi}", exception)
        }
        val image = renderFshImage(
            tgi = request.tgi,
            fsh = fshEntry.content(),
            elementIndex = request.elementIndex ?: 0,
            imageIndex = request.imageIndex ?: 0,
        )
        val outputPath = writeBytes(request.outputPath, Base64.getDecoder().decode(image.payloadBase64))
        return ExportedFileModel(
            tgi = request.tgi,
            kind = KnownEntryKind.FSH,
            format = "png",
            outputPath = outputPath.toAbsolutePath().toString(),
            bytesWritten = Files.size(outputPath),
        )
    }

    override fun describeProperty(request: DescribePropertyRequest): PropertyDescription =
        describeProperty(request.id)
            ?: throw InputError("Unknown property ID: ${request.id.toULong().toString(16).uppercase()}")

    override fun decodePropertyValue(request: DecodePropertyValueRequest): DecodedPropertyModel =
        decodePropertyValue(request.id, request.values)
            ?: throw InputError("Unknown property ID: ${request.id.toULong().toString(16).uppercase()}")

    override fun readKeyCfg(request: ReadKeyCfgRequest): KeyCfgModel {
        val maxBytes = request.maxBytes
        if (maxBytes != null && maxBytes <= 0) {
            throw InputError("maxBytes must be > 0")
        }
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)
        val rawEntry = try {
            entry.toRawEntry(handler) as RawEntry
        } catch (exception: Exception) {
            throw DecodeError("Failed to read KEY resource ${request.tgi}", exception)
        }
        val bytes = Input.slurpBytes(rawEntry.input(), handler) as ByteArray
        val slice = if (maxBytes != null && bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes
        return decodeKeyCfgPayload(
            bytes = slice,
            compressed = rawEntry.compressed(),
            tgi = request.tgi,
        )
    }

    override fun readTabBinary(request: ReadTabBinaryRequest): TabBinaryModel {
        val maxBytes = request.maxBytes
        val maxWords = request.maxWords ?: 128
        if (maxBytes != null && maxBytes <= 0) {
            throw InputError("maxBytes must be > 0")
        }
        if (maxWords <= 0) {
            throw InputError("maxWords must be > 0")
        }
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)
        val rawEntry = try {
            entry.toRawEntry(handler) as RawEntry
        } catch (exception: Exception) {
            throw DecodeError("Failed to read TAB resource ${request.tgi}", exception)
        }
        val bytes = Input.slurpBytes(rawEntry.input(), handler) as ByteArray
        val slice = if (maxBytes != null && bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes
        return decodeTabBinaryPayload(
            bytes = slice,
            compressed = rawEntry.compressed(),
            tgi = request.tgi,
            maxWords = maxWords,
        )
    }

    override fun readRawEntry(request: ReadRawEntryRequest): RawEntryModel {
        val maxBytes = request.maxBytes
        if (maxBytes != null && maxBytes <= 0) {
            throw InputError("maxBytes must be > 0")
        }
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)
        val rawEntry = try {
            entry.toRawEntry(handler) as RawEntry
        } catch (exception: Exception) {
            throw DecodeError("Failed to read raw entry ${request.tgi}", exception)
        }
        val bytes = Input.slurpBytes(rawEntry.input(), handler) as ByteArray
        val slice = if (maxBytes != null && bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes
        val utf8Preview = slice.toString(StandardCharsets.UTF_8)
            .takeIf { text -> text.isNotEmpty() && text.all { it == '\n' || it == '\r' || it == '\t' || !it.isISOControl() } }

        return RawEntryModel(
            tgi = request.tgi,
            kind = kindForType(request.tgi.type),
            compressed = rawEntry.compressed(),
            size = bytes.size,
            payloadBase64 = Base64.getEncoder().encodeToString(slice),
            payloadHexPreview = slice.joinToString("") { byte -> "%02X".format(byte) },
            utf8Preview = utf8Preview,
        )
    }

    private fun validatePaging(request: ListEntriesRequest) {
        val limit = request.limit
        if (limit != null && limit < 0) {
            throw InputError("limit must be >= 0")
        }
        val offset = request.offset
        if (offset != null && offset < 0) {
            throw InputError("offset must be >= 0")
        }
    }

    private fun validateInspectLimits(request: InspectPackageRequest) {
        val maxNotableEntries = request.maxNotableEntries
        if (maxNotableEntries != null && maxNotableEntries !in 1..200) {
            throw InputError("maxNotableEntries must be between 1 and 200")
        }
        val maxObjectHints = request.maxObjectHints
        if (maxObjectHints != null && maxObjectHints !in 1..200) {
            throw InputError("maxObjectHints must be between 1 and 200")
        }
    }

    private fun packageEntrySummaries(dbpf: io.github.memo33.scdbpf.DbpfFile): List<EntrySummary> =
        CollectionConverters.asJava(dbpf.entries()).map { entry ->
            entry as StreamedEntry
            val tgi = tgiToDomain(entry.tgi())
            val rawEntry = entry.toRawEntry(handler) as RawEntry
            EntrySummary(
                tgi = tgi,
                kind = kindForType(tgi.type),
                size = entry.size().toLong(),
                compressed = rawEntry.compressed(),
                label = entry.tgi().label().takeIf(String::isNotBlank),
            )
        }

    private fun notablePriority(kind: KnownEntryKind): Int = when (kind) {
        KnownEntryKind.EXEMPLAR -> 0
        KnownEntryKind.COHORT -> 1
        KnownEntryKind.LTEXT -> 2
        KnownEntryKind.PNG, KnownEntryKind.FSH -> 3
        KnownEntryKind.S3D -> 4
        KnownEntryKind.SC4PATHS -> 5
        KnownEntryKind.KEYCFG, KnownEntryKind.TAB -> 6
        KnownEntryKind.RUL, KnownEntryKind.EFFDIR -> 7
        KnownEntryKind.UNKNOWN -> 8
    }

    private fun notableReason(entry: EntrySummary): String = when (entry.kind) {
        KnownEntryKind.EXEMPLAR -> "SC4 exemplar; use read_exemplar for properties and semantic hints."
        KnownEntryKind.COHORT -> "SC4 cohort; use read_cohort for inherited/shared properties."
        KnownEntryKind.LTEXT -> "Localized text resource; use read_ltext for contents."
        KnownEntryKind.PNG, KnownEntryKind.FSH -> "Image or texture resource; use read_image_entry/read_fsh for preview metadata."
        KnownEntryKind.S3D -> "3D model resource; use read_s3d for model metadata."
        KnownEntryKind.SC4PATHS -> "Network path resource; use read_sc4paths for path records."
        KnownEntryKind.KEYCFG -> "Keyboard/config resource; use read_keycfg for heuristic decoding."
        KnownEntryKind.TAB -> "Compiled TAB resource; use read_tab_binary for structural probing."
        KnownEntryKind.RUL, KnownEntryKind.EFFDIR -> "Known SC4 resource type that is not semantically decoded yet."
        KnownEntryKind.UNKNOWN -> "Unknown type with a label; use read_raw_entry for previews."
    }

    private fun inspectExemplarLikeEntry(
        entry: StreamedEntry,
        warnings: MutableList<String>,
    ): Sc4ObjectHint? {
        val tgi = tgiToDomain(entry.tgi())
        val exemplar = try {
            decodeExemplarEntry(entry).content()
        } catch (exception: Exception) {
            warnings += "Could not decode ${formatTgi(tgi)} for SC4 object hints: ${exception.message}"
            return null
        }
        val properties = decodeProperties(exemplar)
        val exemplarType = properties.firstOrNull { it.id == 0x00000010L }
            ?.decodedValues
            ?.firstOrNull()
            ?.label
        val parent = exemplar.parent()
        val resourceKeys = properties
            .filter { it.id in resourceKeyPropertyIds }
            .flatMap(::resourceKeysFromProperty)
        return Sc4ObjectHint(
            tgi = tgi,
            objectClass = objectClassFor(exemplarType, properties),
            name = exemplarName(properties),
            exemplarType = exemplarType,
            propertyCount = properties.size,
            parentCohort = if (isBlankTgi(parent)) null else tgiToDomain(parent),
            transitEnabled = properties.any { it.id in setOf(0xE90E25A1L, 0xE90E25A2L, 0xE90E25A3L) },
            resourceKeys = resourceKeys.distinct(),
        )
    }

    private fun objectClassFor(exemplarType: String?, properties: List<ExemplarProperty>): String {
        if (properties.any { it.id in 0x88EDC900L..0x88EDCDFFL }) {
            return "Lot"
        }
        if (properties.any { it.id in setOf(0xE90E25A1L, 0xE90E25A2L, 0xE90E25A3L) }) {
            return "Transit-enabled Building"
        }
        return when (exemplarType) {
            "Lot Configuration" -> "Lot"
            "Prop" -> "Prop"
            "Flora" -> "Flora"
            "Building", "Exemplar" -> "Building or exemplar"
            "Network" -> "Network"
            null -> "Exemplar"
            else -> exemplarType
        }
    }

    private fun exemplarName(properties: List<ExemplarProperty>): String? =
        properties.firstOrNull { maybeExemplarName(it.id) }
            ?.values
            ?.firstOrNull()
            ?.jsonPrimitive
            ?.contentOrNull

    private fun resourceKeysFromProperty(property: ExemplarProperty): List<Tgi> {
        val values = property.values.mapNotNull { value ->
            (value as? JsonPrimitive)?.contentOrNull?.trim()?.toLongOrNull()
        }
        return values.chunked(3)
            .filter { it.size == 3 }
            .map { Tgi(type = it[0], group = it[1], instance = it[2]) }
    }

    private fun resolveParentChain(
        dbpf: io.github.memo33.scdbpf.DbpfFile,
        firstParent: ScTgi,
        packagePath: String,
        rootPath: String?,
    ): List<ParentChainItem> {
        if (isBlankTgi(firstParent)) return emptyList()

        val chain = mutableListOf<ParentChainItem>()
        val seen = mutableSetOf<Tgi>()
        var current = tgiToDomain(firstParent)
        var currentDbpf = dbpf
        var currentPackagePath = File(packagePath).absolutePath
        while (true) {
            if (!seen.add(current)) {
                chain += ParentChainItem(
                    tgi = current,
                    resolved = false,
                    warning = "Cycle detected while resolving parent cohorts.",
                )
                return chain
            }
            var entry = findEntryOrNull(currentDbpf, current)
            if (entry == null && rootPath != null) {
                when (val lookup = pluginIndexer.findIndexedEntry(rootPath, current, setOf(KnownEntryKind.COHORT))) {
                    is IndexedEntryLookup.Found -> {
                        currentPackagePath = lookup.packagePath
                        currentDbpf = readPackage(lookup.packagePath)
                        entry = findEntryOrNull(currentDbpf, current)
                    }
                    is IndexedEntryLookup.Unavailable -> {
                        chain += ParentChainItem(
                            tgi = current,
                            resolved = false,
                            warning = lookup.warning,
                        )
                        return chain
                    }
                    IndexedEntryLookup.NotFound -> Unit
                }
            }
            if (entry == null) {
                chain += ParentChainItem(
                    tgi = current,
                    resolved = false,
                    warning = if (rootPath == null) {
                        "Parent cohort not found in this package."
                    } else {
                        "Parent cohort not found in this package or indexed Plugins root."
                    },
                )
                return chain
            }
            val cohort = try {
                decodeExemplarEntry(entry).content()
            } catch (exception: Exception) {
                chain += ParentChainItem(
                    tgi = current,
                    resolved = false,
                    warning = "Failed to decode parent cohort: ${exception.message}",
                )
                return chain
            }
            val properties = decodeProperties(cohort)
            chain += ParentChainItem(
                tgi = current,
                name = exemplarName(properties),
                propertyCount = properties.size,
                resolved = true,
                sourcePackagePath = currentPackagePath,
            )
            val next = cohort.parent()
            if (isBlankTgi(next)) return chain
            current = tgiToDomain(next)
        }
    }

    private fun readPackage(path: String): io.github.memo33.scdbpf.DbpfFile {
        val file = requireDbpfPackageFile(path)
        return try {
            DbpfFile.read(file, handler) as io.github.memo33.scdbpf.DbpfFile
        } catch (exception: Exception) {
            throw PackageError("Failed to read DBPF package: ${file.absolutePath}", exception)
        }
    }

    private fun decodeExemplarForText(path: String, tgi: Tgi, expectedType: Long): Exemplar {
        val dbpf = readPackage(path)
        val entry = findEntry(dbpf, tgi)
        if (tgiToDomain(entry.tgi()).type != expectedType) {
            val kind = if (expectedType == SC4TypeIds.EXEMPLAR) "exemplar" else "cohort"
            throw InputError("Requested entry is not a $kind")
        }
        return try {
            decodeExemplarEntry(entry).content()
        } catch (exception: Exception) {
            throw DecodeError("Failed to decode exemplar-like entry $tgi", exception)
        }
    }

    private fun decodeSC4PathForExport(path: String, tgi: Tgi): Sc4Path {
        val dbpf = readPackage(path)
        val entry = findEntry(dbpf, tgi)
        if (tgiToDomain(entry.tgi()).type != SC4TypeIds.SC4PATHS) {
            throw InputError("Requested entry is not an SC4PATHS entry")
        }
        return try {
            decodeSc4PathEntry(entry).content()
        } catch (exception: Exception) {
            throw DecodeError("Failed to decode SC4PATHS $tgi", exception)
        }
    }

    private fun writeTextExport(model: TextEntryModel, outputPath: String): ExportedFileModel {
        val path = writeBytes(outputPath, model.text.toByteArray(StandardCharsets.UTF_8))
        return ExportedFileModel(
            tgi = model.tgi,
            kind = model.kind,
            format = model.format,
            outputPath = path.toAbsolutePath().toString(),
            bytesWritten = Files.size(path),
        )
    }

    private fun writeBytes(outputPath: String, bytes: ByteArray): Path {
        val path = Path.of(outputPath).toAbsolutePath().normalize()
        path.parent?.let(Files::createDirectories)
        Files.write(path, bytes)
        return path
    }

    private fun findEntry(dbpf: io.github.memo33.scdbpf.DbpfFile, tgi: Tgi): StreamedEntry =
        findEntryOrNull(dbpf, tgi)
            ?: throw PackageError("Entry not found for TGI $tgi")

    private fun findEntryOrNull(dbpf: io.github.memo33.scdbpf.DbpfFile, tgi: Tgi): StreamedEntry? =
        CollectionConverters.asJava(dbpf.entries())
            .map { it as StreamedEntry }
            .firstOrNull { tgiToDomain(it.tgi()) == tgi }

    private fun tgiToDomain(tgi: ScTgi): Tgi = Tgi(
        type = unsignedInt(tgi.tid()),
        group = unsignedInt(tgi.gid()),
        instance = unsignedInt(tgi.iid()),
    )

    private fun formatTgi(tgi: Tgi): String =
        "${formatHex32(tgi.type)}-${formatHex32(tgi.group)}-${formatHex32(tgi.instance)}"

    private fun unsignedInt(value: Any): Long = (value as Number).toLong() and 0xFFFF_FFFFL

    private fun isBlankTgi(tgi: ScTgi): Boolean =
        unsignedInt(tgi.tid()) == 0L && unsignedInt(tgi.gid()) == 0L && unsignedInt(tgi.iid()) == 0L

    @Suppress("UNCHECKED_CAST")
    private fun scalaMapEntries(
        properties: Map<UInt, DbpfProperty.PropertyList<*>>
    ): List<Pair<UInt, DbpfProperty.PropertyList<*>>> =
        CollectionConverters.asJava(properties).entries.map { entry ->
            entry.key to entry.value
        }

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

    private fun readNativePngEntry(entry: StreamedEntry, tgi: Tgi): ImageEntryModel {
        val rawEntry = try {
            entry.toRawEntry(handler) as RawEntry
        } catch (exception: Exception) {
            throw DecodeError("Failed to read PNG ${tgi}", exception)
        }
        val bytes = Input.slurpBytes(rawEntry.input(), handler) as ByteArray
        val image: BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw DecodeError("Failed to decode PNG ${tgi}: unsupported image data")
        return ImageEntryModel(
            tgi = tgi,
            kind = kindForType(tgi.type),
            format = "PNG",
            mimeType = "image/png",
            width = image.getWidth(),
            height = image.getHeight(),
            payloadBase64 = Base64.getEncoder().encodeToString(bytes),
        )
    }

    private fun renderFshImage(
        tgi: Tgi,
        fsh: Fsh,
        elementIndex: Int,
        imageIndex: Int,
    ): ImageEntryModel {
        if (elementIndex < 0 || imageIndex < 0) {
            throw InputError("elementIndex and imageIndex must be >= 0")
        }
        val elements = CollectionConverters.asJava(fsh.elements())
        if (elementIndex >= elements.size) {
            throw InputError("FSH elementIndex out of range: $elementIndex")
        }
        val element = elements[elementIndex]
        val images = CollectionConverters.asJava(element.images()).toList()
        if (imageIndex >= images.size) {
            throw InputError("FSH imageIndex out of range: $imageIndex")
        }
        val image = images[imageIndex]
        val pngBytes = encodeImageAsPng(image)
        return ImageEntryModel(
            tgi = tgi,
            kind = kindForType(tgi.type),
            format = element.format().toString(),
            mimeType = "image/png",
            width = image.width(),
            height = image.height(),
            elementIndex = elementIndex,
            imageIndex = imageIndex,
            payloadBase64 = Base64.getEncoder().encodeToString(pngBytes),
        )
    }

    private fun decodeProperties(exemplar: Exemplar): List<ExemplarProperty> =
        scalaMapEntries(exemplar.properties()).map { (id, propertyList) ->
            val propertyId = id.toLong()
            val expectedType = canonicalPropertyType(describeProperty(propertyId)?.type)
            val values = propertyValues(propertyList)
            val decoded = decodePropertyValue(propertyId, values)
            ExemplarProperty(
                id = propertyId,
                name = propertyName(propertyId),
                valueType = propertyList.valueType().toString(),
                expectedType = expectedType,
                typeMatchesExpected = typesAreCompatible(propertyList.valueType().toString(), expectedType),
                values = values,
                decodedValues = decoded?.values,
                semanticType = decoded?.semanticType,
                interpretation = decoded?.interpretation,
            )
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

    private fun scalaOptionToNullable(value: scala.Option<String>): String? =
        if (value.isDefined) value.get() else null

    private fun tupleToCoordinate(value: scala.Tuple3<Any, Any, Any>): SC4PathCoordinate =
        SC4PathCoordinate(
            x = (value._1() as Number).toFloat(),
            y = (value._2() as Number).toFloat(),
            z = (value._3() as Number).toFloat(),
        )

    private fun encodeImageAsPng(image: Image<RGBA>): ByteArray {
        val bufferedImage = BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height()) {
            for (x in 0 until image.width()) {
                val rgba = image.apply(x, y)
                val argb = ((rgba.alpha().toInt() and 0xFF) shl 24) or
                    ((rgba.red().toInt() and 0xFF) shl 16) or
                    ((rgba.green().toInt() and 0xFF) shl 8) or
                    (rgba.blue().toInt() and 0xFF)
                bufferedImage.setRGB(x, y, argb)
            }
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "png", output)
        return output.toByteArray()
    }
}
