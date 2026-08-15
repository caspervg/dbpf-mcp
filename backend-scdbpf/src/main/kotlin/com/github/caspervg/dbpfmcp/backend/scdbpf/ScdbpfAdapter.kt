package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.CohortModel
import com.github.caspervg.dbpfmcp.core.DecodeQfsRequest
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
import com.github.caspervg.dbpfmcp.core.QfsDecodedModel
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
import com.github.caspervg.dbpfmcp.core.ExemplarPropertyInput
import com.github.caspervg.dbpfmcp.core.ExemplarWriteEntry
import com.github.caspervg.dbpfmcp.core.WriteExemplarsRequest
import com.github.caspervg.dbpfmcp.core.WriteExemplarsResult
import com.github.caspervg.dbpfmcp.core.LTextWriteEntry
import com.github.caspervg.dbpfmcp.core.WriteLTextRequest
import com.github.caspervg.dbpfmcp.core.WriteLTextResult
import com.github.caspervg.dbpfmcp.core.FshElementInput
import com.github.caspervg.dbpfmcp.core.FshWriteEntry
import com.github.caspervg.dbpfmcp.core.WriteFshRequest
import com.github.caspervg.dbpfmcp.core.WriteFshResult
import com.github.caspervg.dbpfmcp.core.ReadIniRequest
import com.github.caspervg.dbpfmcp.core.ReadIniResult
import com.github.caspervg.dbpfmcp.core.WriteIniRequest
import com.github.caspervg.dbpfmcp.core.WriteIniResult
import com.github.caspervg.dbpfmcp.core.RawWriteEntry
import com.github.caspervg.dbpfmcp.core.WriteRawEntriesRequest
import com.github.caspervg.dbpfmcp.core.WriteRawEntriesResult
import com.github.caspervg.dbpfmcp.semantics.EXEMPLAR_TYPE_PROPERTY_ID
import com.github.caspervg.dbpfmcp.semantics.formatHex32
import com.github.caspervg.dbpfmcp.semantics.SC4TypeIds
import com.github.caspervg.dbpfmcp.semantics.canonicalPropertyType
import com.github.caspervg.dbpfmcp.semantics.decodePropertyValue
import com.github.caspervg.dbpfmcp.semantics.describeProperty
import com.github.caspervg.dbpfmcp.semantics.isTransitEnabled
import com.github.caspervg.dbpfmcp.semantics.kindForType
import com.github.caspervg.dbpfmcp.semantics.maybeExemplarName
import com.github.caspervg.dbpfmcp.semantics.objectClassFor
import com.github.caspervg.dbpfmcp.semantics.propertyName
import com.github.caspervg.dbpfmcp.semantics.resourceKeyPropertyIds
import com.github.caspervg.dbpfmcp.semantics.resourceKeysFrom
import com.github.caspervg.dbpfmcp.semantics.typesAreCompatible
import io.github.memo33.passera.unsigned.UByte
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
import io.github.memo33.scdbpf.RawType
import io.github.memo33.scdbpf.S3d
import io.github.memo33.scdbpf.Sc4Path
import io.github.memo33.scdbpf.StreamedEntry
import io.github.memo33.scdbpf.Tgi as ScTgi
import io.github.memo33.scdbpf.`DbpfFile$` as ScDbpfFileObject
import io.github.memo33.scdbpf.`Exemplar$` as ScExemplarObject
import io.github.memo33.scdbpf.DbpfProperty.`ValueType$` as ScValueTypeObject
import io.github.memo33.scdbpf.`DbpfProperty$ValueType$ValueType` as ScValueType
import io.github.memo33.scdbpf.Fsh.`FshFormat$` as ScFshFormatObject
import io.github.memo33.scdbpf.Fsh.`FshDirectoryId$` as ScFshDirIdObject
import io.github.memo33.scdbpf.`Fsh$FshFormat$FshFmtVal` as ScFshFmtVal
import io.github.memo33.scdbpf.`Fsh$FshDirectoryId$FshDirVal` as ScFshDirVal
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import scala.Tuple2
import scala.Option as ScalaOption
import scala.collection.IterableOnce
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

    private val pluginIndexer = ScdbpfPluginIndexer()
    private val entryExplainer = ScdbpfEntryExplainer()
    private val json = Json { prettyPrint = true }

    override fun listEntries(request: ListEntriesRequest): ListEntriesResult {
        validatePaging(request)
        val dbpf = readPackage(request.path)
        val entries = CollectionConverters.asJava(dbpf.entries()).map { entry ->
            entry as StreamedEntry
        }
        val limit = request.limit
        val offset = request.offset ?: 0
        val labelContains = request.labelContains

        // Filter on the cheap TGI/label data first. Decoding each entry to read its compression
        // flag is the expensive part, so it now happens only for the page actually returned.
        val matching = entries.asSequence()
            .map { entry -> entry to tgiToDomain(entry.tgi()) }
            .filter { (_, tgi) -> request.typeFilter == null || tgi.type == request.typeFilter }
            .filter { (_, tgi) -> request.groupFilter == null || tgi.group == request.groupFilter }
            .filter { (_, tgi) -> request.kindFilter == null || kindForType(tgi.type) == request.kindFilter }
            .filter { (entry, _) ->
                labelContains == null ||
                    entry.tgi().label().contains(labelContains, ignoreCase = true)
            }
            .toList()

        val page = matching.asSequence()
            .drop(offset)
            .let { sequence -> if (limit != null) sequence.take(limit) else sequence }
            .map { (entry, tgi) ->
                EntrySummary(
                    tgi = tgi,
                    kind = kindForType(tgi.type),
                    size = entry.size().toLong(),
                    compressed = (entry.toRawEntry(dbpfHandler) as RawEntry).compressed(),
                    label = entry.tgi().label().takeIf(String::isNotBlank),
                )
            }
            .toList()

        return ListEntriesResult(
            packagePath = File(request.path).absolutePath,
            entryCount = entries.size,
            matchCount = matching.size,
            offset = offset,
            limit = limit,
            truncated = offset + page.size < matching.size,
            entries = page,
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
        val warnings = mutableListOf<String>()
        val properties = decodeProperties(exemplar, warnings)
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
            warnings = warnings,
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
        val warnings = mutableListOf<String>()
        val properties = decodeProperties(cohort, warnings)
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
            warnings = warnings,
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

    override fun decodeQfs(request: DecodeQfsRequest): QfsDecodedModel {
        val maxBytes = request.maxBytes
        if (maxBytes != null && maxBytes <= 0) {
            throw InputError("maxBytes must be > 0")
        }
        val input = try {
            Base64.getDecoder().decode(request.payloadBase64)
        } catch (_: IllegalArgumentException) {
            throw InputError("payloadBase64 is not valid base64")
        }
        val offset = when (request.hasDbpfSizePrefix) {
            true -> 4
            false -> 0
            null -> when {
                QfsDecoder.isQfsCompressed(input, 0) -> 0
                input.size >= 6 && QfsDecoder.isQfsCompressed(input, 4) -> 4
                else -> 0
            }
        }
        if (offset == 4 && input.size < 6) {
            throw DecodeError("QFS payload is too short for a DBPF compressed-size prefix")
        }
        val decoded = QfsDecoder.decode(input, offset)
            ?: throw DecodeError("Input is not a valid QFS stream")
        val slice = if (maxBytes != null && decoded.bytes.size > maxBytes) decoded.bytes.copyOf(maxBytes) else decoded.bytes
        return QfsDecodedModel(
            compressedSize = input.size,
            decodedSize = decoded.bytes.size,
            declaredDecodedSize = decoded.declaredSize,
            hasDbpfSizePrefix = offset == 4,
            extendedHeader = decoded.extendedHeader,
            payloadBase64 = Base64.getEncoder().encodeToString(slice),
            payloadHexPreview = slice.joinToString("") { byte -> "%02X".format(byte) },
            utf8Preview = utf8Preview(slice),
        )
    }

    override fun readKeyCfg(request: ReadKeyCfgRequest): KeyCfgModel {
        val maxBytes = request.maxBytes
        if (maxBytes != null && maxBytes <= 0) {
            throw InputError("maxBytes must be > 0")
        }
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)
        // Decode before truncating: slicing a QFS stream would make it undecodable.
        val decoded = decodeEntry(entry, request.tgi)
        val bytes = decoded.bytes
        val slice = if (maxBytes != null && bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes
        return decodeKeyCfgPayload(
            bytes = slice,
            compressed = decoded.compressed,
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
        // Decode before truncating: slicing a QFS stream would make it undecodable.
        val decoded = decodeEntry(entry, request.tgi)
        val bytes = decoded.bytes
        val slice = if (maxBytes != null && bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes
        return decodeTabBinaryPayload(
            bytes = slice,
            compressed = decoded.compressed,
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
            entry.toRawEntry(dbpfHandler) as RawEntry
        } catch (exception: Exception) {
            throw DecodeError("Failed to read raw entry ${request.tgi}", exception)
        }
        val bytes = Input.slurpBytes(rawEntry.input(), dbpfHandler) as ByteArray
        val slice = if (maxBytes != null && bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes
        return RawEntryModel(
            tgi = request.tgi,
            kind = kindForType(request.tgi.type),
            compressed = rawEntry.compressed(),
            size = bytes.size,
            payloadBase64 = Base64.getEncoder().encodeToString(slice),
            payloadHexPreview = slice.joinToString("") { byte -> "%02X".format(byte) },
            utf8Preview = utf8Preview(slice),
        )
    }

    override fun writeExemplars(request: WriteExemplarsRequest): WriteExemplarsResult {
        if (request.entries.isEmpty()) {
            throw InputError("entries must not be empty")
        }
        val seenEntryTgis = mutableSetOf<Tgi>()
        request.entries.forEach { entry ->
            if (!seenEntryTgis.add(entry.tgi)) {
                throw InputError("Duplicate TGI ${formatTgi(entry.tgi)} in entries")
            }
        }
        val warnings = mutableListOf<String>()
        val newEntries = request.entries.map { entry ->
            buildBufferedExemplarEntry(entry, request.compressed, request.validateAgainstRegistry, warnings)
        }
        val (outputFile, entryCount) = writeDbpfPackage(
            outputPath = request.outputPath,
            overwrite = request.overwrite,
            merge = request.merge,
            newEntries = newEntries,
        )
        return WriteExemplarsResult(
            outputPath = outputFile.absolutePath,
            entryCount = entryCount,
            bytesWritten = outputFile.length(),
            warnings = warnings,
        )
    }

    override fun writeLText(request: WriteLTextRequest): WriteLTextResult {
        if (request.entries.isEmpty()) {
            throw InputError("entries must not be empty")
        }
        val seenEntryTgis = mutableSetOf<Tgi>()
        request.entries.forEach { entry ->
            if (!seenEntryTgis.add(entry.tgi)) {
                throw InputError("Duplicate TGI ${formatTgi(entry.tgi)} in entries")
            }
        }
        val newEntries = request.entries.map { entry ->
            BufferedEntry.apply(domainToScTgi(entry.tgi), LText.apply(entry.text), request.compressed)
        }
        val (outputFile, entryCount) = writeDbpfPackage(
            outputPath = request.outputPath,
            overwrite = request.overwrite,
            merge = request.merge,
            newEntries = newEntries,
        )
        return WriteLTextResult(
            outputPath = outputFile.absolutePath,
            entryCount = entryCount,
            bytesWritten = outputFile.length(),
        )
    }

    override fun writeFsh(request: WriteFshRequest): WriteFshResult {
        if (request.entries.isEmpty()) {
            throw InputError("entries must not be empty")
        }
        val seenEntryTgis = mutableSetOf<Tgi>()
        request.entries.forEach { entry ->
            if (!seenEntryTgis.add(entry.tgi)) {
                throw InputError("Duplicate TGI ${formatTgi(entry.tgi)} in entries")
            }
        }
        val warnings = mutableListOf<String>()
        val newEntries = request.entries.map { entry -> buildBufferedFshEntry(entry, request.compressed, warnings) }
        val (outputFile, entryCount) = writeDbpfPackage(
            outputPath = request.outputPath,
            overwrite = request.overwrite,
            merge = request.merge,
            newEntries = newEntries,
        )
        return WriteFshResult(
            outputPath = outputFile.absolutePath,
            entryCount = entryCount,
            bytesWritten = outputFile.length(),
            warnings = warnings,
        )
    }

    private fun writeDbpfPackage(
        outputPath: String,
        overwrite: Boolean,
        merge: Boolean,
        newEntries: List<BufferedEntry<out DbpfType>>,
    ): Pair<File, Int> {
        val outputFile = Path.of(outputPath).toAbsolutePath().normalize().toFile()
        if (!merge && outputFile.exists() && !overwrite) {
            throw InputError(
                "Output file already exists: ${outputFile.absolutePath}. " +
                    "Set overwrite=true to replace it entirely, or merge=true to patch/append entries into it.",
            )
        }
        outputFile.parentFile?.let { it.mkdirs() }

        val newTgis = newEntries.map { tgiToDomain(it.tgi()) }.toSet()
        val entriesToWrite: List<io.github.memo33.scdbpf.DbpfEntry> = if (merge && outputFile.exists()) {
            val existing = readPackage(outputFile.absolutePath)
            val kept = CollectionConverters.asJava(existing.entries())
                .map { it as StreamedEntry }
                .filter { tgiToDomain(it.tgi()) !in newTgis }
            (kept as List<io.github.memo33.scdbpf.DbpfEntry>) + (newEntries as List<io.github.memo33.scdbpf.DbpfEntry>)
        } else {
            newEntries
        }

        try {
            ScDbpfFileObject.`MODULE$`.write(
                CollectionConverters.asScala(entriesToWrite),
                outputFile,
                ScalaOption.empty<UInt>(),
                ScalaOption.empty<UInt>(),
                dbpfHandler,
            )
        } catch (exception: Exception) {
            throw PackageError(
                "Failed to write DBPF package to ${outputFile.absolutePath}: ${exception.message}",
                exception,
            )
        }

        return outputFile to entriesToWrite.size
    }

    private fun buildBufferedFshEntry(
        entry: FshWriteEntry,
        compressed: Boolean,
        warnings: MutableList<String>,
    ): BufferedEntry<Fsh> {
        if (entry.elements.isEmpty()) {
            throw InputError("FSH entry ${formatTgi(entry.tgi)} must declare at least one element")
        }
        val dirId = buildFshDirId(entry.dirId, entry.tgi)
        val elements = entry.elements.map { buildFshElement(entry.tgi, it, warnings) }
        val fsh = Fsh.apply(CollectionConverters.asScala(elements).toIndexedSeq(), dirId)
        return BufferedEntry.apply(domainToScTgi(entry.tgi), fsh, compressed)
    }

    private fun buildFshElement(
        tgi: Tgi,
        elementInput: FshElementInput,
        warnings: MutableList<String>,
    ): Fsh.FshElement {
        if (elementInput.imagesPngBase64.isEmpty()) {
            throw InputError(
                "FSH element on entry ${formatTgi(tgi)} must declare at least one PNG image (mip level 0 = full resolution)",
            )
        }
        val formatName = elementInput.format.trim()
        val fmt = buildFshFmtVal(formatName, tgi)
        val isDxt = formatName == "Dxt1" || formatName == "Dxt3" || formatName == "Dxt5"
        val decoded = elementInput.imagesPngBase64.mapIndexed { index, base64 ->
            val bytes = try {
                Base64.getDecoder().decode(base64)
            } catch (exception: IllegalArgumentException) {
                throw InputError("FSH element image at mip $index on entry ${formatTgi(tgi)} is not valid base64")
            }
            ImageIO.read(ByteArrayInputStream(bytes))
                ?: throw InputError("FSH element image at mip $index on entry ${formatTgi(tgi)} could not be decoded as PNG")
        }
        val baseWidth = decoded[0].width
        val baseHeight = decoded[0].height
        decoded.forEachIndexed { index, image ->
            val expectedWidth = maxOf(1, baseWidth shr index)
            val expectedHeight = maxOf(1, baseHeight shr index)
            if (image.width != expectedWidth || image.height != expectedHeight) {
                throw InputError(
                    "FSH element image at mip $index on entry ${formatTgi(tgi)} is ${image.width}x${image.height}, " +
                        "expected ${expectedWidth}x${expectedHeight} for a mip chain halving down from " +
                        "${baseWidth}x${baseHeight} at mip 0",
                )
            }
            if (isDxt && (image.width % 4 != 0 || image.height % 4 != 0)) {
                throw InputError(
                    "FSH element image at mip $index on entry ${formatTgi(tgi)} is ${image.width}x${image.height}, " +
                        "but $formatName requires both dimensions to be multiples of 4",
                )
            }
        }
        if (decoded.size == 1) {
            warnings += "FSH element on entry ${formatTgi(tgi)}: only 1 mip level (full resolution) provided; " +
                "the game will generate no additional mip levels for this element."
        }
        val images = decoded.map(::toScalaRgbaImage)
        val labelOption: ScalaOption<String> = elementInput.label?.let { ScalaOption.apply(it) } ?: ScalaOption.empty()
        return Fsh.FshElement(CollectionConverters.asScala(images), fmt, labelOption)
    }

    private fun toScalaRgbaImage(bufferedImage: BufferedImage): Image<RGBA> {
        val w = bufferedImage.width
        val h = bufferedImage.height
        return object : Image<RGBA> {
            override fun width(): Int = w
            override fun height(): Int = h
            override fun apply(x: Int, y: Int): RGBA {
                val argb = bufferedImage.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                return RGBA((a shl 24) or (b shl 16) or (g shl 8) or r)
            }
        }
    }

    private fun buildFshFmtVal(name: String, tgi: Tgi): ScFshFmtVal = when (name) {
        "Dxt1" -> ScFshFormatObject.`MODULE$`.Dxt1()
        "Dxt3" -> ScFshFormatObject.`MODULE$`.Dxt3()
        "A8R8G8B8" -> ScFshFormatObject.`MODULE$`.A8R8G8B8()
        "A0R8G8B8" -> ScFshFormatObject.`MODULE$`.A0R8G8B8()
        "A1R5G5B5" -> ScFshFormatObject.`MODULE$`.A1R5G5B5()
        "A0R5G6B5" -> ScFshFormatObject.`MODULE$`.A0R5G6B5()
        "A4R4G4B4" -> ScFshFormatObject.`MODULE$`.A4R4G4B4()
        else -> throw InputError(
            "Unsupported FSH format '$name' on entry ${formatTgi(tgi)}. Supported: Dxt1, Dxt3, A8R8G8B8, " +
                "A0R8G8B8, A1R5G5B5, A0R5G6B5, A4R4G4B4. (Dxt5 encoding is not supported by the bundled scdbpf " +
                "version; decode of Dxt5 entries is unaffected.)",
        )
    }

    private fun buildFshDirId(name: String, tgi: Tgi): ScFshDirVal = when (name.trim().uppercase()) {
        "G354" -> ScFshDirIdObject.`MODULE$`.G354()
        "G264" -> ScFshDirIdObject.`MODULE$`.G264()
        "G266" -> ScFshDirIdObject.`MODULE$`.G266()
        "G290" -> ScFshDirIdObject.`MODULE$`.G290()
        "G315" -> ScFshDirIdObject.`MODULE$`.G315()
        "GIMX" -> ScFshDirIdObject.`MODULE$`.GIMX()
        "G344" -> ScFshDirIdObject.`MODULE$`.G344()
        "G231" -> ScFshDirIdObject.`MODULE$`.G231()
        "G341" -> ScFshDirIdObject.`MODULE$`.G341()
        "G349" -> ScFshDirIdObject.`MODULE$`.G349()
        "G352" -> ScFshDirIdObject.`MODULE$`.G352()
        "G357" -> ScFshDirIdObject.`MODULE$`.G357()
        else -> throw InputError(
            "Unsupported FSH directory id '$name' on entry ${formatTgi(tgi)}. Supported: G354, G264, G266, G290, " +
                "G315, GIMX, G344, G231, G341, G349, G352, G357.",
        )
    }

    override fun readIni(request: ReadIniRequest): ReadIniResult {
        val dbpf = readPackage(request.path)
        val entry = findEntry(dbpf, request.tgi)
        val decoded = decodeEntry(entry, request.tgi)
        return ReadIniResult(
            path = File(request.path).absolutePath,
            tgi = request.tgi,
            compressed = decoded.compressed,
            size = decoded.bytes.size,
            text = String(decoded.bytes, StandardCharsets.UTF_8),
        )
    }

    override fun writeIni(request: WriteIniRequest): WriteIniResult {
        if (request.text.isEmpty()) {
            throw InputError("text must not be empty")
        }
        val entry = BufferedEntry.apply(
            domainToScTgi(request.tgi),
            RawType.apply(request.text.toByteArray(StandardCharsets.UTF_8)),
            request.compressed,
        )
        val (outputFile, entryCount) = writeDbpfPackage(
            outputPath = request.outputPath,
            overwrite = request.overwrite,
            merge = request.merge,
            newEntries = listOf(entry),
        )
        return WriteIniResult(
            outputPath = outputFile.absolutePath,
            tgi = request.tgi,
            entryCount = entryCount,
            bytesWritten = outputFile.length(),
        )
    }

    override fun writeRawEntries(request: WriteRawEntriesRequest): WriteRawEntriesResult {
        if (request.entries.isEmpty()) {
            throw InputError("entries must not be empty")
        }
        val seenEntryTgis = mutableSetOf<Tgi>()
        request.entries.forEach { entry ->
            if (!seenEntryTgis.add(entry.tgi)) {
                throw InputError("Duplicate TGI ${formatTgi(entry.tgi)} in entries")
            }
        }
        val newEntries = request.entries.map { entry -> buildBufferedRawEntry(entry, request.compressed) }
        val (outputFile, entryCount) = writeDbpfPackage(
            outputPath = request.outputPath,
            overwrite = request.overwrite,
            merge = request.merge,
            newEntries = newEntries,
        )
        return WriteRawEntriesResult(
            outputPath = outputFile.absolutePath,
            entryCount = entryCount,
            bytesWritten = outputFile.length(),
        )
    }

    private fun buildBufferedRawEntry(entry: RawWriteEntry, compressed: Boolean): BufferedEntry<DbpfType> {
        val bytes = try {
            Base64.getDecoder().decode(entry.payloadBase64)
        } catch (exception: IllegalArgumentException) {
            throw InputError("Entry ${formatTgi(entry.tgi)} payloadBase64 is not valid base64")
        }
        return BufferedEntry.apply(domainToScTgi(entry.tgi), RawType.apply(bytes), compressed)
    }

    private fun buildBufferedExemplarEntry(
        entry: ExemplarWriteEntry,
        compressed: Boolean,
        validate: Boolean,
        warnings: MutableList<String>,
    ): BufferedEntry<Exemplar> {
        if (entry.properties.isEmpty()) {
            throw InputError("Exemplar ${formatTgi(entry.tgi)} must declare at least one property")
        }
        if (validate) {
            if (entry.isCohort && entry.tgi.type != SC4TypeIds.COHORT) {
                warnings += "Exemplar ${formatTgi(entry.tgi)}: isCohort=true but TGI type is not the standard " +
                    "Cohort type (${formatHex32(SC4TypeIds.COHORT)}). This is allowed but unusual."
            }
            if (!entry.isCohort && entry.tgi.type != SC4TypeIds.EXEMPLAR) {
                warnings += "Exemplar ${formatTgi(entry.tgi)}: isCohort=false but TGI type is not the standard " +
                    "Exemplar type (${formatHex32(SC4TypeIds.EXEMPLAR)}). This is allowed but unusual."
            }
        }
        val seenIds = mutableSetOf<Long>()
        val propTuples = entry.properties.map { property ->
            if (!seenIds.add(property.id)) {
                throw InputError("Duplicate property id ${formatHex32(property.id)} for exemplar ${formatTgi(entry.tgi)}")
            }
            Tuple2(UInt(property.id.toInt()), buildPropertyList(entry.tgi, property, validate, warnings))
        }
        @Suppress("UNCHECKED_CAST")
        val scalaProps = CollectionConverters.asScala(propTuples) as
            IterableOnce<Tuple2<UInt, DbpfProperty.PropertyList<*>>>
        val parentTgi = entry.parentCohort?.let(::domainToScTgi) ?: ScTgi.Blank()
        val exemplar = ScExemplarObject.`MODULE$`.apply(parentTgi, entry.isCohort, scalaProps)
        return BufferedEntry.apply(domainToScTgi(entry.tgi), exemplar, compressed)
    }

    private fun domainToScTgi(tgi: Tgi): ScTgi =
        ScTgi.apply(tgi.type.toInt(), tgi.group.toInt(), tgi.instance.toInt())

    private fun buildPropertyList(
        tgi: Tgi,
        property: ExemplarPropertyInput,
        validate: Boolean,
        warnings: MutableList<String>,
    ): DbpfProperty.PropertyList<*> {
        if (property.values.isEmpty()) {
            throw InputError("Property ${formatHex32(property.id)} on exemplar ${formatTgi(tgi)} must declare at least one value")
        }
        val declaredType = property.type?.trim()?.takeIf { it.isNotEmpty() }
        val registryType = canonicalPropertyType(describeProperty(property.id)?.type)
        val resolvedType = declaredType ?: registryType
            ?: throw InputError(
                "Property ${formatHex32(property.id)} on exemplar ${formatTgi(tgi)} has no type and is not in the " +
                    "bundled property registry; specify type explicitly (Uint8, Uint16, Uint32, Sint32, Sint64, " +
                    "Float32, Bool, String, or Tgi).",
            )
        if (validate) {
            if (declaredType == null) {
                warnings += "Property ${formatHex32(property.id)} on exemplar ${formatTgi(tgi)}: type inferred as " +
                    "$resolvedType from the bundled property registry."
            } else if (registryType == null) {
                warnings += "Property ${formatHex32(property.id)} on exemplar ${formatTgi(tgi)}: not found in the " +
                    "bundled property registry; writing as declared type $declaredType without cross-check."
            } else if (canonicalPropertyType(declaredType) != registryType) {
                warnings += "Property ${formatHex32(property.id)} on exemplar ${formatTgi(tgi)}: declared type " +
                    "$declaredType does not match registry type $registryType. Using declared type as specified."
            }
        }
        return when (resolvedType) {
            "Uint8" -> wrapProperty(
                property.values.map { UByte(requireRangedLong(it, property.id, tgi, 0, 0xFF, "Uint8").toInt().toByte()) },
                ScValueTypeObject.`MODULE$`.Uint8(),
            )
            "Uint16" -> wrapProperty(
                property.values.map { UShort(requireRangedLong(it, property.id, tgi, 0, 0xFFFF, "Uint16").toInt().toShort()) },
                ScValueTypeObject.`MODULE$`.Uint16(),
            )
            "Uint32" -> wrapProperty(
                property.values.map { UInt(requireRangedLong(it, property.id, tgi, 0, 0xFFFFFFFFL, "Uint32").toInt()) },
                ScValueTypeObject.`MODULE$`.Uint32(),
            )
            "Sint32" -> wrapProperty(
                property.values.map {
                    requireRangedLong(it, property.id, tgi, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong(), "Sint32").toInt()
                },
                ScValueTypeObject.`MODULE$`.Sint32(),
            )
            "Sint64" -> wrapProperty(
                property.values.map { requireLong(it, property.id, tgi) },
                ScValueTypeObject.`MODULE$`.Sint64(),
            )
            "Float32" -> wrapProperty(
                property.values.map { requireDouble(it, property.id, tgi).toFloat() },
                ScValueTypeObject.`MODULE$`.Float32(),
            )
            "Bool" -> wrapProperty(
                property.values.map { requireBoolean(it, property.id, tgi) },
                ScValueTypeObject.`MODULE$`.Bool(),
            )
            "String" -> {
                if (property.values.size != 1) {
                    throw InputError(
                        "String property ${formatHex32(property.id)} on exemplar ${formatTgi(tgi)} must have exactly one value",
                    )
                }
                DbpfProperty.Single(requireString(property.values[0], property.id, tgi), ScValueTypeObject.`MODULE$`.String())
            }
            "Tgi" -> {
                val words = property.values.flatMap { requireTgiTriplet(it, property.id, tgi) }
                DbpfProperty.Multi(CollectionConverters.asScala(words).toIndexedSeq(), ScValueTypeObject.`MODULE$`.Uint32())
            }
            else -> throw InputError(
                "Unsupported property type '${property.type}' for property ${formatHex32(property.id)} on exemplar " +
                    "${formatTgi(tgi)}. Supported types: Uint8, Uint16, Uint32, Sint32, Sint64, Float32, Bool, String, Tgi.",
            )
        }
    }

    private fun <A> wrapProperty(values: List<A>, valueType: ScValueType<A>): DbpfProperty.PropertyList<*> =
        if (values.size == 1) {
            DbpfProperty.Single(values[0], valueType)
        } else {
            DbpfProperty.Multi(CollectionConverters.asScala(values).toIndexedSeq(), valueType)
        }

    private fun requireTgiTriplet(value: JsonElement, id: Long, tgi: Tgi): List<UInt> {
        val array = value as? JsonArray
            ?: throw InputError(
                "Property ${formatHex32(id)} on exemplar ${formatTgi(tgi)} has type Tgi and expects each value to " +
                    "be a [type, group, instance] array, got $value",
            )
        if (array.size != 3) {
            throw InputError(
                "Property ${formatHex32(id)} on exemplar ${formatTgi(tgi)} has type Tgi and expects exactly 3 " +
                    "elements (type, group, instance) per value, got ${array.size}",
            )
        }
        return array.map { UInt(requireLong(it, id, tgi).toInt()) }
    }

    private fun requireRangedLong(value: JsonElement, id: Long, tgi: Tgi, min: Long, max: Long, typeName: String): Long {
        val parsed = requireLong(value, id, tgi)
        if (parsed < min || parsed > max) {
            throw InputError(
                "Property ${formatHex32(id)} on exemplar ${formatTgi(tgi)}: value $parsed is out of range for " +
                    "$typeName ($min..$max)",
            )
        }
        return parsed
    }

    private fun requireLong(value: JsonElement, id: Long, tgi: Tgi): Long {
        val primitive = value as? JsonPrimitive
            ?: throw InputError("Property ${formatHex32(id)} on exemplar ${formatTgi(tgi)} expects an integer value, got $value")
        primitive.longOrNull?.let { return it }
        val content = primitive.contentOrNull?.trim()
        if (content != null && content.startsWith("0x", ignoreCase = true)) {
            return content.drop(2).toULongOrNull(16)?.toLong()
                ?: throw InputError("Property ${formatHex32(id)} on exemplar ${formatTgi(tgi)} has an invalid hex integer: $content")
        }
        throw InputError("Property ${formatHex32(id)} on exemplar ${formatTgi(tgi)} expects an integer value, got $value")
    }

    private fun requireDouble(value: JsonElement, id: Long, tgi: Tgi): Double =
        (value as? JsonPrimitive)?.doubleOrNull
            ?: throw InputError("Property ${formatHex32(id)} on exemplar ${formatTgi(tgi)} expects a numeric value, got $value")

    private fun requireBoolean(value: JsonElement, id: Long, tgi: Tgi): Boolean =
        (value as? JsonPrimitive)?.let { it.booleanOrNull ?: it.longOrNull?.let { n -> n != 0L } }
            ?: throw InputError("Property ${formatHex32(id)} on exemplar ${formatTgi(tgi)} expects a boolean value, got $value")

    private fun requireString(value: JsonElement, id: Long, tgi: Tgi): String =
        (value as? JsonPrimitive)?.contentOrNull
            ?: throw InputError("Property ${formatHex32(id)} on exemplar ${formatTgi(tgi)} expects a string value, got $value")

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
            val rawEntry = entry.toRawEntry(dbpfHandler) as RawEntry
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
        // decodeProperties has to be inside the guard too: it decodes every property value, and
        // used to throw straight out of inspect_package on the first registry type mismatch.
        val exemplar: Exemplar
        val properties: List<ExemplarProperty>
        try {
            exemplar = decodeExemplarEntry(entry).content()
            properties = decodeProperties(exemplar, warnings)
        } catch (exception: Exception) {
            warnings += "Could not decode ${formatTgi(tgi)} for SC4 object hints: ${exception.message}"
            return null
        }
        val exemplarType = properties.firstOrNull { it.id == EXEMPLAR_TYPE_PROPERTY_ID }
            ?.decodedValues
            ?.firstOrNull()
            ?.label
        val parent = exemplar.parent()
        val propertyIds = properties.map { it.id }
        val resourceKeys = properties
            .filter { it.id in resourceKeyPropertyIds }
            .flatMap { resourceKeysFrom(it.values) }
        return Sc4ObjectHint(
            tgi = tgi,
            objectClass = objectClassFor(exemplarType, propertyIds),
            name = exemplarName(properties),
            exemplarType = exemplarType,
            propertyCount = properties.size,
            parentCohort = if (isBlankTgi(parent)) null else tgiToDomain(parent),
            transitEnabled = isTransitEnabled(propertyIds),
            resourceKeys = resourceKeys.distinct(),
        )
    }

    private fun exemplarName(properties: List<ExemplarProperty>): String? =
        properties.firstOrNull { maybeExemplarName(it.id) }
            ?.values
            ?.firstOrNull()
            ?.jsonPrimitive
            ?.contentOrNull

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

    private fun utf8Preview(bytes: ByteArray): String? =
        bytes.toString(StandardCharsets.UTF_8)
            .takeIf { text -> text.isNotEmpty() && text.all { it == '\n' || it == '\r' || it == '\t' || !it.isISOControl() } }

    private fun readNativePngEntry(entry: StreamedEntry, tgi: Tgi): ImageEntryModel {
        val bytes = entryBytes(entry, tgi)
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

    /**
     * Decodes every property of an exemplar.
     *
     * A property whose stored value disagrees with the bundled registry's declared type is
     * reported, not fatal: it comes back with its raw values, `typeMatchesExpected = false`, and a
     * warning. Modded exemplars routinely diverge from the registry, and a single such property
     * used to abort the entire tool call.
     */
    private fun decodeProperties(
        exemplar: Exemplar,
        warnings: MutableList<String>? = null,
    ): List<ExemplarProperty> =
        scalaMapEntries(exemplar.properties()).map { (id, propertyList) ->
            val propertyId = id.toLong()
            val actualType = propertyList.valueType().toString()
            val expectedType = canonicalPropertyType(describeProperty(propertyId)?.type)
            val values = propertyValues(propertyList)
            val decoded = try {
                decodePropertyValue(propertyId, values)
            } catch (exception: Exception) {
                warnings?.add(
                    "Property ${formatHex32(propertyId)} does not match its registered type " +
                        "($actualType stored, $expectedType expected); returning raw values.",
                )
                null
            }
            ExemplarProperty(
                id = propertyId,
                name = propertyName(propertyId),
                valueType = actualType,
                expectedType = expectedType,
                typeMatchesExpected = if (decoded == null && expectedType != null) {
                    false
                } else {
                    typesAreCompatible(actualType, expectedType)
                },
                values = values,
                decodedValues = decoded?.values,
                semanticType = decoded?.semanticType,
                interpretation = decoded?.interpretation,
            )
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
