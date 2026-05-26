package com.github.caspervg.dbpfmcp.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Tgi(
    val type: Long,
    val group: Long,
    val instance: Long,
)

@Serializable
enum class KnownEntryKind {
    EXEMPLAR,
    COHORT,
    LTEXT,
    PNG,
    KEYCFG,
    TAB,
    RUL,
    EFFDIR,
    FSH,
    S3D,
    SC4PATHS,
    UNKNOWN,
}

@Serializable
data class EntrySummary(
    val tgi: Tgi,
    val kind: KnownEntryKind,
    val size: Long? = null,
    val compressed: Boolean? = null,
    val label: String? = null,
)

@Serializable
data class ExemplarProperty(
    val id: Long,
    val name: String? = null,
    val valueType: String,
    val expectedType: String? = null,
    val typeMatchesExpected: Boolean? = null,
    val values: List<JsonElement>,
    val decodedValues: List<DecodedPropertyValue>? = null,
    val semanticType: String? = null,
    val interpretation: JsonElement? = null,
    val rawHex: String? = null,
)

@Serializable
data class ExemplarModel(
    val tgi: Tgi,
    val parentCohort: Tgi? = null,
    val exemplarName: String? = null,
    val properties: List<ExemplarProperty>,
    val parentChain: List<ParentChainItem> = emptyList(),
)

@Serializable
data class CohortModel(
    val tgi: Tgi,
    val parentCohort: Tgi? = null,
    val cohortName: String? = null,
    val properties: List<ExemplarProperty>,
    val parentChain: List<ParentChainItem> = emptyList(),
)

@Serializable
data class ParentChainItem(
    val tgi: Tgi,
    val name: String? = null,
    val propertyCount: Int? = null,
    val resolved: Boolean,
    val sourcePackagePath: String? = null,
    val warning: String? = null,
)

@Serializable
data class LTextModel(
    val tgi: Tgi,
    val text: String,
    val length: Int,
)

@Serializable
data class SC4PathCoordinate(
    val x: Float,
    val y: Float,
    val z: Float,
)

@Serializable
data class SC4PathRecord(
    val comment: String? = null,
    val transportType: String,
    val classNumber: Int,
    val entry: String,
    val exit: String,
    val junction: Boolean,
    val coords: List<SC4PathCoordinate>,
)

@Serializable
data class SC4StopPathRecord(
    val comment: String? = null,
    val uk: Boolean,
    val transportType: String,
    val classNumber: Int,
    val entry: String,
    val exit: String,
    val coord: SC4PathCoordinate,
)

@Serializable
data class SC4PathsModel(
    val tgi: Tgi,
    val terrainVariance: Boolean,
    val pathCount: Int,
    val stopPathCount: Int,
    val textPreview: String,
    val paths: List<SC4PathRecord>,
    val stopPaths: List<SC4StopPathRecord>,
)

@Serializable
data class ListEntriesRequest(
    val path: String,
    val limit: Int? = null,
    val offset: Int? = null,
    val typeFilter: Long? = null,
    val groupFilter: Long? = null,
    val kindFilter: KnownEntryKind? = null,
    val labelContains: String? = null,
)

@Serializable
data class ListEntriesResult(
    val packagePath: String,
    val entryCount: Int,
    val entries: List<EntrySummary>,
)

@Serializable
data class SummarizePackageRequest(
    val path: String,
)

@Serializable
data class InspectPackageRequest(
    val path: String,
    val maxNotableEntries: Int? = null,
    val maxObjectHints: Int? = null,
)

@Serializable
data class IndexPluginsRequest(
    val rootPath: String,
    val forceRefresh: Boolean = false,
    val maxFiles: Int? = null,
)

@Serializable
data class IndexStatusRequest(
    val rootPath: String,
)

@Serializable
data class SearchIndexRequest(
    val rootPath: String,
    val query: String? = null,
    val kindFilter: KnownEntryKind? = null,
    val objectClass: String? = null,
    val propertyId: Long? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

@Serializable
data class ExplainEntryRequest(
    val path: String,
    val tgi: Tgi,
)

@Serializable
data class PackageKindSummary(
    val kind: KnownEntryKind,
    val count: Int,
)

@Serializable
data class PackageSummary(
    val packagePath: String,
    val entryCount: Int,
    val compressedCount: Int,
    val uncompressedCount: Int,
    val countsByKind: List<PackageKindSummary>,
    val topLabels: List<String>,
)

@Serializable
data class NotableEntry(
    val tgi: Tgi,
    val kind: KnownEntryKind,
    val label: String? = null,
    val size: Long? = null,
    val reason: String,
)

@Serializable
data class Sc4ObjectHint(
    val tgi: Tgi,
    val objectClass: String,
    val name: String? = null,
    val exemplarType: String? = null,
    val propertyCount: Int,
    val parentCohort: Tgi? = null,
    val transitEnabled: Boolean = false,
    val resourceKeys: List<Tgi> = emptyList(),
)

@Serializable
data class InspectPackageResult(
    val packagePath: String,
    val entryCount: Int,
    val compressedCount: Int,
    val uncompressedCount: Int,
    val countsByKind: List<PackageKindSummary>,
    val topLabels: List<String>,
    val notableEntries: List<NotableEntry>,
    val sc4ObjectHints: List<Sc4ObjectHint>,
    val warnings: List<String>,
    val recommendedNextTools: List<String>,
)

@Serializable
data class IndexPluginsResult(
    val rootPath: String,
    val cachePath: String,
    val fileCount: Int,
    val indexedFileCount: Int,
    val entryCount: Int,
    val skippedFileCount: Int,
    val warningCount: Int,
    val warnings: List<String>,
    val builtAtEpochMillis: Long,
)

@Serializable
data class IndexStatusResult(
    val rootPath: String,
    val cachePath: String,
    val exists: Boolean,
    val fileCount: Int,
    val entryCount: Int,
    val staleIndexedFileCount: Int,
    val missingIndexedFileCount: Int,
    val builtAtEpochMillis: Long? = null,
)

@Serializable
data class SearchIndexMatch(
    val packagePath: String,
    val tgi: Tgi,
    val kind: KnownEntryKind,
    val label: String? = null,
    val exemplarName: String? = null,
    val exemplarType: String? = null,
    val objectClass: String? = null,
    val matchedField: String,
    val context: String,
)

@Serializable
data class SearchIndexResult(
    val rootPath: String,
    val cachePath: String,
    val totalMatches: Int,
    val offset: Int,
    val limit: Int,
    val matches: List<SearchIndexMatch>,
)

@Serializable
data class ExplanationField(
    val name: String,
    val value: String,
)

@Serializable
data class ExplanationRelationship(
    val kind: String,
    val tgi: Tgi? = null,
    val label: String? = null,
    val resolved: Boolean? = null,
)

@Serializable
data class ExplainEntryResult(
    val packagePath: String,
    val tgi: Tgi,
    val kind: KnownEntryKind,
    val summary: String,
    val importantFields: List<ExplanationField>,
    val relationships: List<ExplanationRelationship>,
    val warnings: List<String>,
    val suggestedNextTools: List<String>,
)

@Serializable
data class ReadExemplarRequest(
    val path: String,
    val tgi: Tgi,
    val resolveParent: Boolean = false,
    val rootPath: String? = null,
)

@Serializable
data class ReadCohortRequest(
    val path: String,
    val tgi: Tgi,
    val resolveParent: Boolean = false,
    val rootPath: String? = null,
)

@Serializable
data class ReadExemplarTextRequest(
    val path: String,
    val tgi: Tgi,
)

@Serializable
data class ReadCohortTextRequest(
    val path: String,
    val tgi: Tgi,
)

@Serializable
data class ExportExemplarTextRequest(
    val path: String,
    val tgi: Tgi,
    val outputPath: String,
)

@Serializable
data class ExportCohortTextRequest(
    val path: String,
    val tgi: Tgi,
    val outputPath: String,
)

@Serializable
data class ReadLTextRequest(
    val path: String,
    val tgi: Tgi,
)

@Serializable
data class ReadSC4PathsRequest(
    val path: String,
    val tgi: Tgi,
)

@Serializable
data class ExportSC4PathsTextRequest(
    val path: String,
    val tgi: Tgi,
    val outputPath: String,
)

@Serializable
data class ExportSC4PathsJsonRequest(
    val path: String,
    val tgi: Tgi,
    val outputPath: String,
)

@Serializable
data class ReadS3dRequest(
    val path: String,
    val tgi: Tgi,
)

@Serializable
data class ReadFshRequest(
    val path: String,
    val tgi: Tgi,
    val previewElementIndex: Int? = null,
    val previewImageIndex: Int? = null,
)

@Serializable
data class ReadImageEntryRequest(
    val path: String,
    val tgi: Tgi,
    val elementIndex: Int? = null,
    val imageIndex: Int? = null,
)

@Serializable
data class ExportFshPngRequest(
    val path: String,
    val tgi: Tgi,
    val outputPath: String,
    val elementIndex: Int? = null,
    val imageIndex: Int? = null,
)

@Serializable
data class DescribePropertyRequest(
    val id: Long,
)

@Serializable
data class DecodePropertyValueRequest(
    val id: Long,
    val values: List<JsonElement>,
)

@Serializable
data class ReadRawEntryRequest(
    val path: String,
    val tgi: Tgi,
    val maxBytes: Int? = null,
)

@Serializable
data class ReadKeyCfgRequest(
    val path: String,
    val tgi: Tgi,
    val maxBytes: Int? = null,
)

@Serializable
data class ReadTabBinaryRequest(
    val path: String,
    val tgi: Tgi,
    val maxBytes: Int? = null,
    val maxWords: Int? = null,
)

@Serializable
data class RawEntryModel(
    val tgi: Tgi,
    val kind: KnownEntryKind,
    val compressed: Boolean,
    val size: Int,
    val payloadBase64: String,
    val payloadHexPreview: String,
    val utf8Preview: String? = null,
)

@Serializable
data class TextEntryModel(
    val tgi: Tgi,
    val kind: KnownEntryKind,
    val format: String,
    val propertyCount: Int? = null,
    val text: String,
)

@Serializable
data class ExportedFileModel(
    val tgi: Tgi,
    val kind: KnownEntryKind,
    val format: String,
    val outputPath: String,
    val bytesWritten: Long,
)

@Serializable
data class PropertyDescription(
    val id: Long,
    val name: String,
    val type: String? = null,
    val description: String? = null,
    val group: String? = null,
)

@Serializable
data class DecodedPropertyValue(
    val index: Int,
    val raw: JsonElement,
    val normalized: JsonElement,
    val decimal: Long? = null,
    val hex: String? = null,
    val text: String? = null,
    val boolean: Boolean? = null,
    val label: String? = null,
)

@Serializable
data class DecodedPropertyModel(
    val property: PropertyDescription,
    val values: List<DecodedPropertyValue>,
    val semanticType: String? = null,
    val interpretation: JsonElement? = null,
)

@Serializable
data class KeyCfgTextFragment(
    val offset: Int,
    val text: String,
)

@Serializable
data class KeyCfgRecord(
    val offset: Int,
    val rawSegments: List<String>,
    val keyCombination: String? = null,
    val messageIds: List<String>,
    val description: String? = null,
)

@Serializable
data class KeyCfgModel(
    val tgi: Tgi,
    val compressed: Boolean,
    val size: Int,
    val formatHint: String,
    val textFragments: List<KeyCfgTextFragment>,
    val records: List<KeyCfgRecord>,
    val notes: List<String>,
)

@Serializable
data class TabBinaryChunk(
    val offset: Int,
    val words: List<String>,
)

@Serializable
data class TabBinaryModel(
    val tgi: Tgi,
    val compressed: Boolean,
    val size: Int,
    val formatHint: String,
    val headerWords: List<String>,
    val words: List<String>,
    val chunks: List<TabBinaryChunk>,
    val notes: List<String>,
)

@Serializable
data class S3dVertGroupSummary(
    val index: Int,
    val vertexCount: Int,
)

@Serializable
data class S3dIndxGroupSummary(
    val index: Int,
    val indexCount: Int,
)

@Serializable
data class S3dPrimSummary(
    val type: String,
    val firstIndex: Int,
    val indexCount: Int,
)

@Serializable
data class S3dPrimGroupSummary(
    val index: Int,
    val primitiveCount: Int,
    val primitives: List<S3dPrimSummary>,
)

@Serializable
data class S3dMaterialSummary(
    val id: String,
    val wrapU: String,
    val wrapV: String,
    val magFilter: String,
    val minFilter: String,
    val animRate: Int,
    val animMode: Int,
    val name: String? = null,
)

@Serializable
data class S3dMatsGroupSummary(
    val index: Int,
    val flags: List<String>,
    val alphaFunc: String,
    val depthFunc: String,
    val sourceBlend: String,
    val destBlend: String,
    val alphaThreshold: Int,
    val materialCount: Int,
    val materials: List<S3dMaterialSummary>,
)

@Serializable
data class S3dAnimGroupSummary(
    val index: Int,
    val name: String? = null,
    val flags: Int,
    val frameBlockCount: Int,
)

@Serializable
data class S3dPropSummary(
    val meshIndex: Int,
    val frameIndex: Int,
    val assignmentType: String,
    val assignedValue: String,
)

@Serializable
data class S3dRegpSummary(
    val name: String,
    val transformCount: Int,
)

@Serializable
data class S3dModel(
    val tgi: Tgi,
    val vertGroupCount: Int,
    val indxGroupCount: Int,
    val primGroupCount: Int,
    val matsGroupCount: Int,
    val propCount: Int,
    val regpCount: Int,
    val totalVertices: Int,
    val totalIndices: Int,
    val totalPrimitives: Int,
    val animFrameCount: Int,
    val animFrameRate: Int,
    val animPlayMode: String,
    val animDisplacement: Float,
    val vertGroups: List<S3dVertGroupSummary>,
    val indxGroups: List<S3dIndxGroupSummary>,
    val primGroups: List<S3dPrimGroupSummary>,
    val matsGroups: List<S3dMatsGroupSummary>,
    val animGroups: List<S3dAnimGroupSummary>,
    val props: List<S3dPropSummary>,
    val regpGroups: List<S3dRegpSummary>,
)

@Serializable
data class FshImageSummary(
    val index: Int,
    val width: Int,
    val height: Int,
    val mipLevel: Int,
)

@Serializable
data class FshElementSummary(
    val index: Int,
    val format: String,
    val label: String? = null,
    val imageCount: Int,
    val images: List<FshImageSummary>,
)

@Serializable
data class FshModel(
    val tgi: Tgi,
    val dirId: String,
    val elementCount: Int,
    val imageCount: Int,
    val elements: List<FshElementSummary>,
    val preview: ImageEntryModel? = null,
)

@Serializable
data class ImageEntryModel(
    val tgi: Tgi,
    val kind: KnownEntryKind,
    val format: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val elementIndex: Int? = null,
    val imageIndex: Int? = null,
    val payloadBase64: String,
)

interface DbpfService {
    val backendName: String

    fun listEntries(request: ListEntriesRequest): ListEntriesResult

    fun summarizePackage(request: SummarizePackageRequest): PackageSummary

    fun inspectPackage(request: InspectPackageRequest): InspectPackageResult

    fun indexPlugins(request: IndexPluginsRequest): IndexPluginsResult

    fun indexStatus(request: IndexStatusRequest): IndexStatusResult

    fun searchIndex(request: SearchIndexRequest): SearchIndexResult

    fun explainEntry(request: ExplainEntryRequest): ExplainEntryResult

    fun readExemplar(request: ReadExemplarRequest): ExemplarModel

    fun readCohort(request: ReadCohortRequest): CohortModel

    fun readExemplarText(request: ReadExemplarTextRequest): TextEntryModel

    fun readCohortText(request: ReadCohortTextRequest): TextEntryModel

    fun exportExemplarText(request: ExportExemplarTextRequest): ExportedFileModel

    fun exportCohortText(request: ExportCohortTextRequest): ExportedFileModel

    fun readLText(request: ReadLTextRequest): LTextModel

    fun readSC4Paths(request: ReadSC4PathsRequest): SC4PathsModel

    fun readSC4PathsText(request: ReadSC4PathsRequest): TextEntryModel

    fun exportSC4PathsText(request: ExportSC4PathsTextRequest): ExportedFileModel

    fun exportSC4PathsJson(request: ExportSC4PathsJsonRequest): ExportedFileModel

    fun readS3d(request: ReadS3dRequest): S3dModel

    fun readFsh(request: ReadFshRequest): FshModel

    fun readImageEntry(request: ReadImageEntryRequest): ImageEntryModel

    fun exportFshPng(request: ExportFshPngRequest): ExportedFileModel

    fun describeProperty(request: DescribePropertyRequest): PropertyDescription

    fun decodePropertyValue(request: DecodePropertyValueRequest): DecodedPropertyModel

    fun readKeyCfg(request: ReadKeyCfgRequest): KeyCfgModel

    fun readTabBinary(request: ReadTabBinaryRequest): TabBinaryModel

    fun readRawEntry(request: ReadRawEntryRequest): RawEntryModel
}
