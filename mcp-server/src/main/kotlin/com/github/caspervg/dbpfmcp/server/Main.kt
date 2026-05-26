package com.github.caspervg.dbpfmcp.server

import com.github.caspervg.dbpfmcp.backend.scdbpf.ScdbpfAdapter
import com.github.caspervg.dbpfmcp.core.DecodePropertyValueRequest
import com.github.caspervg.dbpfmcp.core.DbpfException
import com.github.caspervg.dbpfmcp.core.ExemplarProperty
import com.github.caspervg.dbpfmcp.core.ExportedFileModel
import com.github.caspervg.dbpfmcp.core.ExportCohortTextRequest
import com.github.caspervg.dbpfmcp.core.ExportExemplarTextRequest
import com.github.caspervg.dbpfmcp.core.ExportFshPngRequest
import com.github.caspervg.dbpfmcp.core.ExportSC4PathsJsonRequest
import com.github.caspervg.dbpfmcp.core.ExportSC4PathsTextRequest
import com.github.caspervg.dbpfmcp.core.ExplainEntryRequest
import com.github.caspervg.dbpfmcp.core.ExplainEntryResult
import com.github.caspervg.dbpfmcp.core.IndexPluginsRequest
import com.github.caspervg.dbpfmcp.core.IndexStatusRequest
import com.github.caspervg.dbpfmcp.core.InspectPackageRequest
import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import com.github.caspervg.dbpfmcp.core.ListEntriesRequest
import com.github.caspervg.dbpfmcp.core.ParentChainItem
import com.github.caspervg.dbpfmcp.core.ReadCohortRequest
import com.github.caspervg.dbpfmcp.core.ReadCohortTextRequest
import com.github.caspervg.dbpfmcp.core.ReadExemplarRequest
import com.github.caspervg.dbpfmcp.core.ReadExemplarTextRequest
import com.github.caspervg.dbpfmcp.core.ReadFshRequest
import com.github.caspervg.dbpfmcp.core.ReadImageEntryRequest
import com.github.caspervg.dbpfmcp.core.ReadKeyCfgRequest
import com.github.caspervg.dbpfmcp.core.ReadLTextRequest
import com.github.caspervg.dbpfmcp.core.ReadRawEntryRequest
import com.github.caspervg.dbpfmcp.core.ReadS3dRequest
import com.github.caspervg.dbpfmcp.core.ReadSC4PathsRequest
import com.github.caspervg.dbpfmcp.core.ReadTabBinaryRequest
import com.github.caspervg.dbpfmcp.core.SearchIndexRequest
import com.github.caspervg.dbpfmcp.core.SummarizePackageRequest
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.semantics.formatHex32
import com.github.caspervg.dbpfmcp.semantics.parseHexId
import com.github.caspervg.dbpfmcp.semantics.parseTgi
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val DEFAULT_LIST_LIMIT = 200
private const val MAX_LIST_LIMIT = 1_000
private const val DEFAULT_RAW_MAX_BYTES = 4_096
private const val DEFAULT_KEYCFG_MAX_BYTES = 65_536
private const val DEFAULT_TAB_MAX_BYTES = 4_096
private const val DEFAULT_TAB_MAX_WORDS = 128
private const val MAX_ENTRY_PREVIEW_BYTES = 262_144
private const val MAX_TAB_WORDS = 4_096

fun main(): Unit = runBlocking {
    val adapter = ScdbpfAdapter()
    val json = Json { prettyPrint = true }
    val closed = CompletableDeferred<Unit>()
    val server = Server(
        serverInfo = Implementation("dbpf-mcp", "0.1.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    ) {
        addTool(
            name = "list_entries",
            description = "List DBPF entries by TGI and basic metadata",
            inputSchema = listEntriesInputSchema(),
            title = "List DBPF Entries",
            toolAnnotations = readOnlyToolAnnotations("List DBPF Entries"),
        ) { request ->
            handleTool(json) {
                val result = adapter.listEntries(parseListEntriesRequest(request))
                buildJsonObject {
                    put("packagePath", result.packagePath)
                    put("entryCount", result.entryCount)
                    putJsonArray("entries") {
                        result.entries.forEach { entry ->
                            add(
                                buildJsonObject {
                                    put("tgi", tgiJson(entry.tgi))
                                    put("kind", entry.kind.name)
                                    put("size", entry.size?.let(::JsonPrimitive) ?: JsonNull)
                                    put("compressed", entry.compressed?.let(::JsonPrimitive) ?: JsonNull)
                                    put("label", entry.label?.let(::JsonPrimitive) ?: JsonNull)
                                }
                            )
                        }
                    }
                }
            }
        }
        addTool(
            name = "summarize_package",
            description = "Summarize a DBPF package by entry kind, compression, and common labels",
            inputSchema = summarizePackageInputSchema(),
            title = "Summarize Package",
            toolAnnotations = readOnlyToolAnnotations("Summarize Package"),
        ) { request ->
            handleTool(json) {
                val result = adapter.summarizePackage(parseSummarizePackageRequest(request))
                buildJsonObject {
                    put("packagePath", result.packagePath)
                    put("entryCount", result.entryCount)
                    put("compressedCount", result.compressedCount)
                    put("uncompressedCount", result.uncompressedCount)
                    putJsonArray("countsByKind") {
                        result.countsByKind.forEach { summary ->
                            add(
                                buildJsonObject {
                                    put("kind", summary.kind.name)
                                    put("count", summary.count)
                                }
                            )
                        }
                    }
                    putJsonArray("topLabels") {
                        result.topLabels.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
        }
        addTool(
            name = "inspect_package",
            description = "Inspect one DBPF package and return bounded, agent-friendly SC4 hints without scanning folders",
            inputSchema = inspectPackageInputSchema(),
            title = "Inspect Package",
            toolAnnotations = readOnlyToolAnnotations("Inspect Package"),
        ) { request ->
            handleTool(json) {
                val result = adapter.inspectPackage(parseInspectPackageRequest(request))
                buildJsonObject {
                    put("packagePath", result.packagePath)
                    put("entryCount", result.entryCount)
                    put("compressedCount", result.compressedCount)
                    put("uncompressedCount", result.uncompressedCount)
                    putJsonArray("countsByKind") {
                        result.countsByKind.forEach { summary ->
                            add(
                                buildJsonObject {
                                    put("kind", summary.kind.name)
                                    put("count", summary.count)
                                }
                            )
                        }
                    }
                    putJsonArray("topLabels") {
                        result.topLabels.forEach { add(JsonPrimitive(it)) }
                    }
                    putJsonArray("notableEntries") {
                        result.notableEntries.forEach { entry ->
                            add(
                                buildJsonObject {
                                    put("tgi", tgiJson(entry.tgi))
                                    put("kind", entry.kind.name)
                                    put("label", entry.label?.let(::JsonPrimitive) ?: JsonNull)
                                    put("size", entry.size?.let(::JsonPrimitive) ?: JsonNull)
                                    put("reason", entry.reason)
                                }
                            )
                        }
                    }
                    putJsonArray("sc4ObjectHints") {
                        result.sc4ObjectHints.forEach { hint ->
                            add(
                                buildJsonObject {
                                    put("tgi", tgiJson(hint.tgi))
                                    put("objectClass", hint.objectClass)
                                    put("name", hint.name?.let(::JsonPrimitive) ?: JsonNull)
                                    put("exemplarType", hint.exemplarType?.let(::JsonPrimitive) ?: JsonNull)
                                    put("propertyCount", hint.propertyCount)
                                    put("parentCohort", hint.parentCohort?.let(::tgiJson) ?: JsonNull)
                                    put("transitEnabled", hint.transitEnabled)
                                    putJsonArray("resourceKeys") {
                                        hint.resourceKeys.forEach { add(tgiJson(it)) }
                                    }
                                }
                            )
                        }
                    }
                    putJsonArray("warnings") {
                        result.warnings.forEach { add(JsonPrimitive(it)) }
                    }
                    putJsonArray("recommendedNextTools") {
                        result.recommendedNextTools.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
        }
        addTool(
            name = "index_plugins",
            description = "Build or refresh a persistent metadata index for a Plugins folder; use before folder-wide search",
            inputSchema = indexPluginsInputSchema(),
            title = "Index Plugins",
            toolAnnotations = readOnlyToolAnnotations("Index Plugins"),
        ) { request ->
            handleTool(json) {
                val result = adapter.indexPlugins(parseIndexPluginsRequest(request))
                buildJsonObject {
                    put("rootPath", result.rootPath)
                    put("cachePath", result.cachePath)
                    put("fileCount", result.fileCount)
                    put("indexedFileCount", result.indexedFileCount)
                    put("entryCount", result.entryCount)
                    put("skippedFileCount", result.skippedFileCount)
                    put("warningCount", result.warningCount)
                    put("builtAtEpochMillis", result.builtAtEpochMillis)
                    putJsonArray("warnings") {
                        result.warnings.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
        }
        addTool(
            name = "index_status",
            description = "Report whether a Plugins folder has a current-ish local metadata index",
            inputSchema = indexStatusInputSchema(),
            title = "Index Status",
            toolAnnotations = readOnlyToolAnnotations("Index Status"),
        ) { request ->
            handleTool(json) {
                val result = adapter.indexStatus(parseIndexStatusRequest(request))
                buildJsonObject {
                    put("rootPath", result.rootPath)
                    put("cachePath", result.cachePath)
                    put("exists", result.exists)
                    put("fileCount", result.fileCount)
                    put("entryCount", result.entryCount)
                    put("staleIndexedFileCount", result.staleIndexedFileCount)
                    put("missingIndexedFileCount", result.missingIndexedFileCount)
                    put("builtAtEpochMillis", result.builtAtEpochMillis?.let(::JsonPrimitive) ?: JsonNull)
                }
            }
        }
        addTool(
            name = "search_index",
            description = "Search a previously built Plugins folder index without recursively scanning the folder",
            inputSchema = searchIndexInputSchema(),
            title = "Search Index",
            toolAnnotations = readOnlyToolAnnotations("Search Index"),
        ) { request ->
            handleTool(json) {
                val result = adapter.searchIndex(parseSearchIndexRequest(request))
                buildJsonObject {
                    put("rootPath", result.rootPath)
                    put("cachePath", result.cachePath)
                    put("totalMatches", result.totalMatches)
                    put("offset", result.offset)
                    put("limit", result.limit)
                    putJsonArray("matches") {
                        result.matches.forEach { match ->
                            add(
                                buildJsonObject {
                                    put("packagePath", match.packagePath)
                                    put("tgi", tgiJson(match.tgi))
                                    put("kind", match.kind.name)
                                    put("label", match.label?.let(::JsonPrimitive) ?: JsonNull)
                                    put("exemplarName", match.exemplarName?.let(::JsonPrimitive) ?: JsonNull)
                                    put("exemplarType", match.exemplarType?.let(::JsonPrimitive) ?: JsonNull)
                                    put("objectClass", match.objectClass?.let(::JsonPrimitive) ?: JsonNull)
                                    put("matchedField", match.matchedField)
                                    put("context", match.context)
                                }
                            )
                        }
                    }
                }
            }
        }
        addTool(
            name = "explain_entry",
            description = "Explain one DBPF entry in concise, agent-friendly SC4 terms",
            inputSchema = readEntryByTgiInputSchema(),
            title = "Explain Entry",
            toolAnnotations = readOnlyToolAnnotations("Explain Entry"),
        ) { request ->
            handleTool(json) {
                explanationJson(adapter.explainEntry(parseExplainEntryRequest(request)))
            }
        }
        addTool(
            name = "read_exemplar",
            description = "Decode a single exemplar entry into a stable semantic JSON model",
            inputSchema = readEntryByTgiInputSchema(includeResolveParent = true),
            title = "Read Exemplar",
            toolAnnotations = readOnlyToolAnnotations("Read Exemplar"),
        ) { request ->
            handleTool(json) {
                val result = adapter.readExemplar(parseReadExemplarRequest(request))
                buildJsonObject {
                    put("tgi", tgiJson(result.tgi))
                    put("parentCohort", result.parentCohort?.let(::tgiJson) ?: JsonNull)
                    put("exemplarName", result.exemplarName?.let(::JsonPrimitive) ?: JsonNull)
                    putJsonArray("parentChain") {
                        result.parentChain.forEach { add(parentChainItemJson(it)) }
                    }
                    putJsonArray("properties") {
                        result.properties.forEach { property -> add(propertyJson(property)) }
                    }
                }
            }
        }
        addTool(
            name = "read_cohort",
            description = "Decode a single cohort entry into a stable semantic JSON model",
            inputSchema = readEntryByTgiInputSchema(includeResolveParent = true),
            title = "Read Cohort",
            toolAnnotations = readOnlyToolAnnotations("Read Cohort"),
        ) { request ->
            handleTool(json) {
                val result = adapter.readCohort(parseReadCohortRequest(request))
                buildJsonObject {
                    put("tgi", tgiJson(result.tgi))
                    put("parentCohort", result.parentCohort?.let(::tgiJson) ?: JsonNull)
                    put("cohortName", result.cohortName?.let(::JsonPrimitive) ?: JsonNull)
                    putJsonArray("parentChain") {
                        result.parentChain.forEach { add(parentChainItemJson(it)) }
                    }
                    putJsonArray("properties") {
                        result.properties.forEach { property -> add(propertyJson(property)) }
                    }
                }
            }
        }
        addTool(
            name = "read_exemplar_text",
            description = "Render one exemplar as canonical SC4 text-exemplar syntax without writing files",
            inputSchema = readEntryByTgiInputSchema(),
            title = "Read Exemplar Text",
            toolAnnotations = readOnlyToolAnnotations("Read Exemplar Text"),
        ) { request ->
            handleTool(json) {
                textEntryJson(adapter.readExemplarText(parseReadExemplarTextRequest(request)))
            }
        }
        addTool(
            name = "read_cohort_text",
            description = "Render one cohort as canonical SC4 text-exemplar syntax without writing files",
            inputSchema = readEntryByTgiInputSchema(),
            title = "Read Cohort Text",
            toolAnnotations = readOnlyToolAnnotations("Read Cohort Text"),
        ) { request ->
            handleTool(json) {
                textEntryJson(adapter.readCohortText(parseReadCohortTextRequest(request)))
            }
        }
        addTool(
            name = "export_exemplar_text",
            description = "Render one exemplar as canonical SC4 text-exemplar syntax and write it to disk",
            inputSchema = exportEntryByTgiInputSchema(),
            title = "Export Exemplar Text",
            toolAnnotations = writeToolAnnotations("Export Exemplar Text"),
        ) { request ->
            handleTool(json) {
                exportedFileJson(adapter.exportExemplarText(parseExportExemplarTextRequest(request)))
            }
        }
        addTool(
            name = "export_cohort_text",
            description = "Render one cohort as canonical SC4 text-exemplar syntax and write it to disk",
            inputSchema = exportEntryByTgiInputSchema(),
            title = "Export Cohort Text",
            toolAnnotations = writeToolAnnotations("Export Cohort Text"),
        ) { request ->
            handleTool(json) {
                exportedFileJson(adapter.exportCohortText(parseExportCohortTextRequest(request)))
            }
        }
        addTool(
            name = "read_ltext",
            description = "Decode a single LTEXT entry into stable text JSON",
            inputSchema = readEntryByTgiInputSchema(),
            title = "Read LTEXT",
            toolAnnotations = readOnlyToolAnnotations("Read LTEXT"),
        ) { request ->
            handleTool(json) {
                val result = adapter.readLText(parseReadLTextRequest(request))
                buildJsonObject {
                    put("tgi", tgiJson(result.tgi))
                    put("text", result.text)
                    put("length", result.length)
                }
            }
        }
        addTool(
            name = "read_sc4paths",
            description = "Decode a single SC4PATHS entry into stable path JSON",
            inputSchema = readEntryByTgiInputSchema(),
            title = "Read SC4PATHS",
            toolAnnotations = readOnlyToolAnnotations("Read SC4PATHS"),
        ) { request ->
            handleTool(json) {
                val result = adapter.readSC4Paths(parseReadSC4PathsRequest(request))
                buildJsonObject {
                    put("tgi", tgiJson(result.tgi))
                    put("terrainVariance", result.terrainVariance)
                    put("pathCount", result.pathCount)
                    put("stopPathCount", result.stopPathCount)
                    put("textPreview", result.textPreview)
                    putJsonArray("paths") {
                        result.paths.forEach { path ->
                            add(
                                buildJsonObject {
                                    put("comment", path.comment?.let(::JsonPrimitive) ?: JsonNull)
                                    put("transportType", path.transportType)
                                    put("classNumber", path.classNumber)
                                    put("entry", path.entry)
                                    put("exit", path.exit)
                                    put("junction", path.junction)
                                    putJsonArray("coords") {
                                        path.coords.forEach { coord ->
                                            add(
                                                buildJsonObject {
                                                    put("x", coord.x)
                                                    put("y", coord.y)
                                                    put("z", coord.z)
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                    putJsonArray("stopPaths") {
                        result.stopPaths.forEach { path ->
                            add(
                                buildJsonObject {
                                    put("comment", path.comment?.let(::JsonPrimitive) ?: JsonNull)
                                    put("uk", path.uk)
                                    put("transportType", path.transportType)
                                    put("classNumber", path.classNumber)
                                    put("entry", path.entry)
                                    put("exit", path.exit)
                                    putJsonObject("coord") {
                                        put("x", path.coord.x)
                                        put("y", path.coord.y)
                                        put("z", path.coord.z)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        addTool(
            name = "read_sc4paths_text",
            description = "Render one SC4PATHS entry as canonical SC4PATHS text without writing files",
            inputSchema = readEntryByTgiInputSchema(),
            title = "Read SC4PATHS Text",
            toolAnnotations = readOnlyToolAnnotations("Read SC4PATHS Text"),
        ) { request ->
            handleTool(json) {
                textEntryJson(adapter.readSC4PathsText(parseReadSC4PathsRequest(request)))
            }
        }
        addTool(
            name = "export_sc4paths_text",
            description = "Render one SC4PATHS entry as canonical SC4PATHS text and write it to disk",
            inputSchema = exportEntryByTgiInputSchema(),
            title = "Export SC4PATHS Text",
            toolAnnotations = writeToolAnnotations("Export SC4PATHS Text"),
        ) { request ->
            handleTool(json) {
                exportedFileJson(adapter.exportSC4PathsText(parseExportSC4PathsTextRequest(request)))
            }
        }
        addTool(
            name = "export_sc4paths_json",
            description = "Decode one SC4PATHS entry into stable JSON and write it to disk",
            inputSchema = exportEntryByTgiInputSchema(),
            title = "Export SC4PATHS JSON",
            toolAnnotations = writeToolAnnotations("Export SC4PATHS JSON"),
        ) { request ->
            handleTool(json) {
                exportedFileJson(adapter.exportSC4PathsJson(parseExportSC4PathsJsonRequest(request)))
            }
        }
        addTool(
            name = "read_s3d",
            description = "Decode a single S3D entry into stable model metadata JSON",
            inputSchema = readEntryByTgiInputSchema(),
            title = "Read S3D",
            toolAnnotations = readOnlyToolAnnotations("Read S3D"),
        ) { request ->
            handleTool(json) {
                val result = adapter.readS3d(parseReadS3dRequest(request))
                buildJsonObject {
                    put("tgi", tgiJson(result.tgi))
                    put("vertGroupCount", result.vertGroupCount)
                    put("indxGroupCount", result.indxGroupCount)
                    put("primGroupCount", result.primGroupCount)
                    put("matsGroupCount", result.matsGroupCount)
                    put("propCount", result.propCount)
                    put("regpCount", result.regpCount)
                    put("totalVertices", result.totalVertices)
                    put("totalIndices", result.totalIndices)
                    put("totalPrimitives", result.totalPrimitives)
                    put("animFrameCount", result.animFrameCount)
                    put("animFrameRate", result.animFrameRate)
                    put("animPlayMode", result.animPlayMode)
                    put("animDisplacement", result.animDisplacement)
                    putJsonArray("vertGroups") {
                        result.vertGroups.forEach { group ->
                            add(buildJsonObject {
                                put("index", group.index)
                                put("vertexCount", group.vertexCount)
                            })
                        }
                    }
                    putJsonArray("indxGroups") {
                        result.indxGroups.forEach { group ->
                            add(buildJsonObject {
                                put("index", group.index)
                                put("indexCount", group.indexCount)
                            })
                        }
                    }
                    putJsonArray("primGroups") {
                        result.primGroups.forEach { group ->
                            add(
                                buildJsonObject {
                                    put("index", group.index)
                                    put("primitiveCount", group.primitiveCount)
                                    putJsonArray("primitives") {
                                        group.primitives.forEach { primitive ->
                                            add(
                                                buildJsonObject {
                                                    put("type", primitive.type)
                                                    put("firstIndex", primitive.firstIndex)
                                                    put("indexCount", primitive.indexCount)
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                    putJsonArray("matsGroups") {
                        result.matsGroups.forEach { group ->
                            add(
                                buildJsonObject {
                                    put("index", group.index)
                                    putJsonArray("flags") {
                                        group.flags.forEach { add(JsonPrimitive(it)) }
                                    }
                                    put("alphaFunc", group.alphaFunc)
                                    put("depthFunc", group.depthFunc)
                                    put("sourceBlend", group.sourceBlend)
                                    put("destBlend", group.destBlend)
                                    put("alphaThreshold", group.alphaThreshold)
                                    put("materialCount", group.materialCount)
                                    putJsonArray("materials") {
                                        group.materials.forEach { material ->
                                            add(
                                                buildJsonObject {
                                                    put("id", material.id)
                                                    put("wrapU", material.wrapU)
                                                    put("wrapV", material.wrapV)
                                                    put("magFilter", material.magFilter)
                                                    put("minFilter", material.minFilter)
                                                    put("animRate", material.animRate)
                                                    put("animMode", material.animMode)
                                                    put("name", material.name?.let(::JsonPrimitive) ?: JsonNull)
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                    putJsonArray("animGroups") {
                        result.animGroups.forEach { group ->
                            add(
                                buildJsonObject {
                                    put("index", group.index)
                                    put("name", group.name?.let(::JsonPrimitive) ?: JsonNull)
                                    put("flags", group.flags)
                                    put("frameBlockCount", group.frameBlockCount)
                                }
                            )
                        }
                    }
                    putJsonArray("props") {
                        result.props.forEach { prop ->
                            add(
                                buildJsonObject {
                                    put("meshIndex", prop.meshIndex)
                                    put("frameIndex", prop.frameIndex)
                                    put("assignmentType", prop.assignmentType)
                                    put("assignedValue", prop.assignedValue)
                                }
                            )
                        }
                    }
                    putJsonArray("regpGroups") {
                        result.regpGroups.forEach { group ->
                            add(
                                buildJsonObject {
                                    put("name", group.name)
                                    put("transformCount", group.transformCount)
                                }
                            )
                        }
                    }
                }
            }
        }
        addTool(
            name = "read_fsh",
            description = "Decode a single FSH entry into stable texture metadata JSON",
            inputSchema = readFshInputSchema(),
            title = "Read FSH",
            toolAnnotations = readOnlyToolAnnotations("Read FSH"),
        ) { request ->
            handleTool(json) {
                val result = adapter.readFsh(parseReadFshRequest(request))
                buildJsonObject {
                    put("tgi", tgiJson(result.tgi))
                    put("dirId", result.dirId)
                    put("elementCount", result.elementCount)
                    put("imageCount", result.imageCount)
                    putJsonArray("elements") {
                        result.elements.forEach { element ->
                            add(
                                buildJsonObject {
                                    put("index", element.index)
                                    put("format", element.format)
                                    put("label", element.label?.let(::JsonPrimitive) ?: JsonNull)
                                    put("imageCount", element.imageCount)
                                    putJsonArray("images") {
                                        element.images.forEach { image ->
                                            add(
                                                buildJsonObject {
                                                    put("index", image.index)
                                                    put("width", image.width)
                                                    put("height", image.height)
                                                    put("mipLevel", image.mipLevel)
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                    put("preview", result.preview?.let(::imageEntryJson) ?: JsonNull)
                }
            }
        }
        addTool(
            name = "read_image_entry",
            description = "Decode one image entry as PNG-ready bytes, supporting native PNG entries and selected FSH bitmaps",
            inputSchema = readImageEntryInputSchema(),
            title = "Read Image Entry",
            toolAnnotations = readOnlyToolAnnotations("Read Image Entry"),
        ) { request ->
            handleTool(json) {
                val result = adapter.readImageEntry(parseReadImageEntryRequest(request))
                imageEntryJson(result)
            }
        }
        addTool(
            name = "export_fsh_png",
            description = "Decode one FSH bitmap and write it to a PNG file",
            inputSchema = exportFshPngInputSchema(),
            title = "Export FSH PNG",
            toolAnnotations = writeToolAnnotations("Export FSH PNG"),
        ) { request ->
            handleTool(json) {
                exportedFileJson(adapter.exportFshPng(parseExportFshPngRequest(request)))
            }
        }
        addTool(
            name = "describe_property",
            description = "Describe one SC4 property from the bundled property registry",
            inputSchema = describePropertyInputSchema(),
            title = "Describe Property",
            toolAnnotations = readOnlyToolAnnotations("Describe Property"),
        ) { request ->
            handleTool(json) {
                val result = adapter.describeProperty(parseDescribePropertyRequest(request))
                buildJsonObject {
                    put("id", formatHex32(result.id))
                    put("name", result.name)
                    put("type", result.type?.let(::JsonPrimitive) ?: JsonNull)
                    put("description", result.description?.let(::JsonPrimitive) ?: JsonNull)
                    put("group", result.group?.let(::JsonPrimitive) ?: JsonNull)
                }
            }
        }
        addTool(
            name = "decode_property_value",
            description = "Decode one property value list using the bundled SC4 property registry",
            inputSchema = decodePropertyValueInputSchema(),
            title = "Decode Property Value",
            toolAnnotations = readOnlyToolAnnotations("Decode Property Value"),
        ) { request ->
            handleTool(json) {
                val result = adapter.decodePropertyValue(parseDecodePropertyValueRequest(request))
                buildJsonObject {
                    putJsonObject("property") {
                        put("id", formatHex32(result.property.id))
                        put("name", result.property.name)
                        put("type", result.property.type?.let(::JsonPrimitive) ?: JsonNull)
                        put("description", result.property.description?.let(::JsonPrimitive) ?: JsonNull)
                        put("group", result.property.group?.let(::JsonPrimitive) ?: JsonNull)
                    }
                    put("semanticType", result.semanticType?.let(::JsonPrimitive) ?: JsonNull)
                    put("interpretation", result.interpretation ?: JsonNull)
                    putJsonArray("values") {
                        result.values.forEach { value ->
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
                }
            }
        }
        addTool(
            name = "read_keycfg",
            description = "Heuristically decode a KEYCFG/TAB-like DBPF entry into text fragments and candidate shortcut records",
            inputSchema = readKeyCfgInputSchema(),
            title = "Read KEYCFG",
            toolAnnotations = readOnlyToolAnnotations("Read KEYCFG"),
        ) { request ->
            handleTool(json) {
                val result = adapter.readKeyCfg(parseReadKeyCfgRequest(request))
                buildJsonObject {
                    put("tgi", tgiJson(result.tgi))
                    put("compressed", result.compressed)
                    put("size", result.size)
                    put("formatHint", result.formatHint)
                    putJsonArray("textFragments") {
                        result.textFragments.forEach { fragment ->
                            add(
                                buildJsonObject {
                                    put("offset", fragment.offset)
                                    put("text", fragment.text)
                                }
                            )
                        }
                    }
                    putJsonArray("records") {
                        result.records.forEach { record ->
                            add(
                                buildJsonObject {
                                    put("offset", record.offset)
                                    put("keyCombination", record.keyCombination?.let(::JsonPrimitive) ?: JsonNull)
                                    putJsonArray("messageIds") {
                                        record.messageIds.forEach { add(JsonPrimitive(it)) }
                                    }
                                    put("description", record.description?.let(::JsonPrimitive) ?: JsonNull)
                                    putJsonArray("rawSegments") {
                                        record.rawSegments.forEach { add(JsonPrimitive(it)) }
                                    }
                                }
                            )
                        }
                    }
                    putJsonArray("notes") {
                        result.notes.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
        }
        addTool(
            name = "read_tab_binary",
            description = "Probe a compiled binary TAB resource as little-endian 32-bit words",
            inputSchema = readTabBinaryInputSchema(),
            title = "Read TAB Binary",
            toolAnnotations = readOnlyToolAnnotations("Read TAB Binary"),
        ) { request ->
            handleTool(json) {
                val result = adapter.readTabBinary(parseReadTabBinaryRequest(request))
                buildJsonObject {
                    put("tgi", tgiJson(result.tgi))
                    put("compressed", result.compressed)
                    put("size", result.size)
                    put("formatHint", result.formatHint)
                    putJsonArray("headerWords") {
                        result.headerWords.forEach { add(JsonPrimitive(it)) }
                    }
                    putJsonArray("words") {
                        result.words.forEach { add(JsonPrimitive(it)) }
                    }
                    putJsonArray("chunks") {
                        result.chunks.forEach { chunk ->
                            add(
                                buildJsonObject {
                                    put("offset", chunk.offset)
                                    putJsonArray("words") {
                                        chunk.words.forEach { add(JsonPrimitive(it)) }
                                    }
                                }
                            )
                        }
                    }
                    putJsonArray("notes") {
                        result.notes.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
        }
        addTool(
            name = "read_raw_entry",
            description = "Read one DBPF entry as raw bytes with stable metadata and previews",
            inputSchema = readRawEntryInputSchema(),
            title = "Read Raw Entry",
            toolAnnotations = readOnlyToolAnnotations("Read Raw Entry"),
        ) { request ->
            handleTool(json) {
                val result = adapter.readRawEntry(parseReadRawEntryRequest(request))
                buildJsonObject {
                    put("tgi", tgiJson(result.tgi))
                    put("kind", result.kind.name)
                    put("compressed", result.compressed)
                    put("size", result.size)
                    put("payloadBase64", result.payloadBase64)
                    put("payloadHexPreview", result.payloadHexPreview)
                    put("utf8Preview", result.utf8Preview?.let(::JsonPrimitive) ?: JsonNull)
                }
            }
        }
    }

    server.onClose { closed.complete(Unit) }
    val transport = StdioServerTransport(
        System.`in`.asSource().buffered(),
        System.out.asSink().buffered(),
    )
    server.createSession(transport)
    closed.await()
}

private suspend fun handleTool(
    json: Json,
    action: () -> JsonObject,
): CallToolResult = try {
    val payload = action()
    CallToolResult(
        content = listOf(TextContent(json.encodeToString(JsonObject.serializer(), payload))),
        structuredContent = payload,
        isError = false,
    )
} catch (exception: DbpfException) {
    errorResult(json, exception.message ?: exception::class.simpleName.orEmpty())
} catch (exception: Exception) {
    errorResult(json, exception.message ?: "Unexpected error")
}

private fun errorResult(json: Json, message: String): CallToolResult {
    val payload = buildJsonObject { put("error", message) }
    return CallToolResult(
        content = listOf(TextContent(json.encodeToString(JsonObject.serializer(), payload))),
        structuredContent = payload,
        isError = true,
    )
}

private fun parseListEntriesRequest(request: CallToolRequest): ListEntriesRequest {
    val args = request.arguments
    return ListEntriesRequest(
        path = args.requiredString("path"),
        limit = args.optionalIntInRange("limit", min = 0, max = MAX_LIST_LIMIT, default = DEFAULT_LIST_LIMIT),
        offset = args.optionalIntInRange("offset", min = 0, max = Int.MAX_VALUE),
        typeFilter = args.optionalString("typeFilterHex")?.let { parseHexId(it, "typeFilterHex") },
        groupFilter = args.optionalString("groupFilterHex")?.let { parseHexId(it, "groupFilterHex") },
        kindFilter = args.optionalString("kindFilter")?.let(::parseKnownEntryKind),
        labelContains = args.optionalString("labelContains"),
    )
}

private fun parseSummarizePackageRequest(request: CallToolRequest): SummarizePackageRequest =
    SummarizePackageRequest(path = request.arguments.requiredString("path"))

private fun parseInspectPackageRequest(request: CallToolRequest): InspectPackageRequest =
    InspectPackageRequest(
        path = request.arguments.requiredString("path"),
        maxNotableEntries = request.arguments.optionalIntInRange("maxNotableEntries", min = 1, max = 200),
        maxObjectHints = request.arguments.optionalIntInRange("maxObjectHints", min = 1, max = 200),
    )

private fun parseIndexPluginsRequest(request: CallToolRequest): IndexPluginsRequest =
    IndexPluginsRequest(
        rootPath = request.arguments.requiredString("rootPath"),
        forceRefresh = request.arguments.optionalBoolean("forceRefresh") ?: false,
        maxFiles = request.arguments.optionalIntInRange("maxFiles", min = 1, max = Int.MAX_VALUE),
    )

private fun parseIndexStatusRequest(request: CallToolRequest): IndexStatusRequest =
    IndexStatusRequest(rootPath = request.arguments.requiredString("rootPath"))

private fun parseSearchIndexRequest(request: CallToolRequest): SearchIndexRequest {
    val args = request.arguments
    return SearchIndexRequest(
        rootPath = args.requiredString("rootPath"),
        query = args.optionalString("query"),
        kindFilter = args.optionalString("kindFilter")?.let(::parseKnownEntryKind),
        objectClass = args.optionalString("objectClass"),
        propertyId = args.optionalString("propertyIdHex")?.let { parseHexId(it, "propertyIdHex") },
        limit = args.optionalIntInRange("limit", min = 1, max = 500),
        offset = args.optionalIntInRange("offset", min = 0, max = Int.MAX_VALUE),
    )
}

private fun parseExplainEntryRequest(request: CallToolRequest): ExplainEntryRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ExplainEntryRequest(path = path, tgi = tgi)
}

private fun parseReadExemplarRequest(request: CallToolRequest): ReadExemplarRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadExemplarRequest(
        path = path,
        tgi = tgi,
        resolveParent = request.arguments.optionalBoolean("resolveParent") ?: false,
        rootPath = request.arguments.optionalString("rootPath"),
    )
}

private fun parseReadCohortRequest(request: CallToolRequest): ReadCohortRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadCohortRequest(
        path = path,
        tgi = tgi,
        resolveParent = request.arguments.optionalBoolean("resolveParent") ?: false,
        rootPath = request.arguments.optionalString("rootPath"),
    )
}

private fun parseReadExemplarTextRequest(request: CallToolRequest): ReadExemplarTextRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadExemplarTextRequest(path = path, tgi = tgi)
}

private fun parseReadCohortTextRequest(request: CallToolRequest): ReadCohortTextRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadCohortTextRequest(path = path, tgi = tgi)
}

private fun parseExportExemplarTextRequest(request: CallToolRequest): ExportExemplarTextRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ExportExemplarTextRequest(
        path = path,
        tgi = tgi,
        outputPath = request.arguments.requiredString("outputPath"),
    )
}

private fun parseExportCohortTextRequest(request: CallToolRequest): ExportCohortTextRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ExportCohortTextRequest(
        path = path,
        tgi = tgi,
        outputPath = request.arguments.requiredString("outputPath"),
    )
}

private fun parseReadLTextRequest(request: CallToolRequest): ReadLTextRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadLTextRequest(path = path, tgi = tgi)
}

private fun parseReadSC4PathsRequest(request: CallToolRequest): ReadSC4PathsRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadSC4PathsRequest(path = path, tgi = tgi)
}

private fun parseExportSC4PathsTextRequest(request: CallToolRequest): ExportSC4PathsTextRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ExportSC4PathsTextRequest(
        path = path,
        tgi = tgi,
        outputPath = request.arguments.requiredString("outputPath"),
    )
}

private fun parseExportSC4PathsJsonRequest(request: CallToolRequest): ExportSC4PathsJsonRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ExportSC4PathsJsonRequest(
        path = path,
        tgi = tgi,
        outputPath = request.arguments.requiredString("outputPath"),
    )
}

private fun parseReadS3dRequest(request: CallToolRequest): ReadS3dRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadS3dRequest(path = path, tgi = tgi)
}

private fun parseReadFshRequest(request: CallToolRequest): ReadFshRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadFshRequest(
        path = path,
        tgi = tgi,
        previewElementIndex = request.arguments.optionalIntInRange("previewElementIndex", min = 0, max = Int.MAX_VALUE),
        previewImageIndex = request.arguments.optionalIntInRange("previewImageIndex", min = 0, max = Int.MAX_VALUE),
    )
}

private fun parseReadImageEntryRequest(request: CallToolRequest): ReadImageEntryRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadImageEntryRequest(
        path = path,
        tgi = tgi,
        elementIndex = request.arguments.optionalIntInRange("elementIndex", min = 0, max = Int.MAX_VALUE),
        imageIndex = request.arguments.optionalIntInRange("imageIndex", min = 0, max = Int.MAX_VALUE),
    )
}

private fun parseExportFshPngRequest(request: CallToolRequest): ExportFshPngRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ExportFshPngRequest(
        path = path,
        tgi = tgi,
        outputPath = request.arguments.requiredString("outputPath"),
        elementIndex = request.arguments.optionalIntInRange("elementIndex", min = 0, max = Int.MAX_VALUE),
        imageIndex = request.arguments.optionalIntInRange("imageIndex", min = 0, max = Int.MAX_VALUE),
    )
}

private fun parseDescribePropertyRequest(request: CallToolRequest) =
    com.github.caspervg.dbpfmcp.core.DescribePropertyRequest(
        id = parseHexId(request.arguments.requiredString("id"), "id"),
    )

private fun parseDecodePropertyValueRequest(request: CallToolRequest): DecodePropertyValueRequest {
    val args = request.arguments
    return DecodePropertyValueRequest(
        id = parseHexId(args.requiredString("id"), "id"),
        values = args.requiredArray("values"),
    )
}

private fun parseReadRawEntryRequest(request: CallToolRequest): ReadRawEntryRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadRawEntryRequest(
        path = path,
        tgi = tgi,
        maxBytes = request.arguments.optionalIntInRange(
            "maxBytes",
            min = 1,
            max = MAX_ENTRY_PREVIEW_BYTES,
            default = DEFAULT_RAW_MAX_BYTES,
        ),
    )
}

private fun parseReadKeyCfgRequest(request: CallToolRequest): ReadKeyCfgRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadKeyCfgRequest(
        path = path,
        tgi = tgi,
        maxBytes = request.arguments.optionalIntInRange(
            "maxBytes",
            min = 1,
            max = MAX_ENTRY_PREVIEW_BYTES,
            default = DEFAULT_KEYCFG_MAX_BYTES,
        ),
    )
}

private fun parseReadTabBinaryRequest(request: CallToolRequest): ReadTabBinaryRequest {
    val (path, tgi) = parseReadEntryRequest(request)
    return ReadTabBinaryRequest(
        path = path,
        tgi = tgi,
        maxBytes = request.arguments.optionalIntInRange(
            "maxBytes",
            min = 1,
            max = MAX_ENTRY_PREVIEW_BYTES,
            default = DEFAULT_TAB_MAX_BYTES,
        ),
        maxWords = request.arguments.optionalIntInRange(
            "maxWords",
            min = 1,
            max = MAX_TAB_WORDS,
            default = DEFAULT_TAB_MAX_WORDS,
        ),
    )
}

private fun parseReadEntryRequest(request: CallToolRequest): Pair<String, Tgi> {
    val args = request.arguments
    val tgiText = args.optionalString("tgi")
    val type = args.optionalString("type")
    val group = args.optionalString("group")
    val instance = args.optionalString("instance")
    val tgi = if (tgiText != null) {
        parseTgi(tgiText)
    } else {
        val missing = listOf(
            "type" to type,
            "group" to group,
            "instance" to instance,
        ).filter { it.second == null }.map { it.first }
        if (missing.isNotEmpty()) {
            throw InputError("Missing TGI arguments: provide either tgi or all of type, group, and instance; missing ${missing.joinToString(", ")}")
        }
        Tgi(
            type = parseHexId(type!!, "type"),
            group = parseHexId(group!!, "group"),
            instance = parseHexId(instance!!, "instance"),
        )
    }
    return args.requiredString("path") to tgi
}

private fun JsonObject.requiredString(name: String): String =
    optionalString(name) ?: throw InputError("Missing required argument: $name")

private fun JsonObject.optionalString(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.requiredArray(name: String): List<JsonElement> =
    this[name]?.jsonArray?.toList() ?: throw InputError("Missing required argument: $name")

private fun JsonObject.optionalInt(name: String): Int? {
    val element = this[name] ?: return null
    return element.jsonPrimitive.intOrNull ?: throw InputError("$name must be an integer")
}

private fun JsonObject.optionalIntInRange(
    name: String,
    min: Int,
    max: Int,
    default: Int? = null,
): Int? {
    val value = optionalInt(name) ?: return default
    if (value < min || value > max) {
        throw InputError("$name must be between $min and $max")
    }
    return value
}

private fun JsonObject.optionalBoolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull

private fun tgiJson(tgi: Tgi): JsonObject = buildJsonObject {
    put("type", formatHex32(tgi.type))
    put("group", formatHex32(tgi.group))
    put("instance", formatHex32(tgi.instance))
}

private fun parentChainItemJson(item: ParentChainItem): JsonObject = buildJsonObject {
    put("tgi", tgiJson(item.tgi))
    put("name", item.name?.let(::JsonPrimitive) ?: JsonNull)
    put("propertyCount", item.propertyCount?.let(::JsonPrimitive) ?: JsonNull)
    put("resolved", item.resolved)
    put("sourcePackagePath", item.sourcePackagePath?.let(::JsonPrimitive) ?: JsonNull)
    put("warning", item.warning?.let(::JsonPrimitive) ?: JsonNull)
}

private fun explanationJson(result: ExplainEntryResult): JsonObject = buildJsonObject {
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

private fun propertyJson(property: ExemplarProperty): JsonObject = buildJsonObject {
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
    put("rawHex", property.rawHex?.let(::JsonPrimitive) ?: JsonNull)
}

private fun imageEntryJson(image: com.github.caspervg.dbpfmcp.core.ImageEntryModel): JsonObject = buildJsonObject {
    put("tgi", tgiJson(image.tgi))
    put("kind", image.kind.name)
    put("format", image.format)
    put("mimeType", image.mimeType)
    put("width", image.width)
    put("height", image.height)
    put("elementIndex", image.elementIndex?.let(::JsonPrimitive) ?: JsonNull)
    put("imageIndex", image.imageIndex?.let(::JsonPrimitive) ?: JsonNull)
    put("payloadBase64", image.payloadBase64)
}

private fun textEntryJson(text: com.github.caspervg.dbpfmcp.core.TextEntryModel): JsonObject = buildJsonObject {
    put("tgi", tgiJson(text.tgi))
    put("kind", text.kind.name)
    put("format", text.format)
    put("propertyCount", text.propertyCount?.let(::JsonPrimitive) ?: JsonNull)
    put("text", text.text)
}

private fun exportedFileJson(file: ExportedFileModel): JsonObject = buildJsonObject {
    put("tgi", tgiJson(file.tgi))
    put("kind", file.kind.name)
    put("format", file.format)
    put("outputPath", file.outputPath)
    put("bytesWritten", file.bytesWritten)
}

private fun parseKnownEntryKind(value: String): KnownEntryKind = try {
    KnownEntryKind.valueOf(value.trim().uppercase())
} catch (_: IllegalArgumentException) {
    throw InputError("Unknown kindFilter: $value. Expected one of: ${knownEntryKindValues()}")
}

private fun knownEntryKindValues(): String = KnownEntryKind.entries.joinToString(", ") { it.name }

private fun readOnlyToolAnnotations(title: String) = ToolAnnotations(
    title = title,
    readOnlyHint = true,
    destructiveHint = false,
    idempotentHint = true,
    openWorldHint = false,
)

private fun writeToolAnnotations(title: String) = ToolAnnotations(
    title = title,
    readOnlyHint = false,
    destructiveHint = false,
    idempotentHint = true,
    openWorldHint = false,
)

private fun listEntriesInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file, not a Plugins folder.")
        }
        putJsonObject("limit") {
            put("type", "integer")
            put("description", "Maximum entries to return, 0-$MAX_LIST_LIMIT. Default: $DEFAULT_LIST_LIMIT. Use offset for paging large packages.")
        }
        putJsonObject("offset") {
            put("type", "integer")
            put("description", "Number of matching entries to skip. Default: 0.")
        }
        putJsonObject("typeFilterHex") {
            put("type", "string")
        }
        putJsonObject("groupFilterHex") {
            put("type", "string")
        }
        putJsonObject("kindFilter") {
            put("type", "string")
            put("description", "Optional KnownEntryKind enum. Expected one of: ${knownEntryKindValues()}.")
        }
        putJsonObject("labelContains") {
            put("type", "string")
        }
    },
    required = listOf("path"),
)

private fun summarizePackageInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file, not a Plugins folder")
        }
    },
    required = listOf("path"),
)

private fun inspectPackageInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file. This tool never recursively scans folders.")
        }
        putJsonObject("maxNotableEntries") {
            put("type", "integer")
            put("description", "Maximum notable entries to return, 1-200. Default: 20.")
        }
        putJsonObject("maxObjectHints") {
            put("type", "integer")
            put("description", "Maximum exemplar/cohort SC4 object hints to return, 1-200. Default: 25.")
        }
    },
    required = listOf("path"),
)

private fun indexPluginsInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("rootPath") {
            put("type", "string")
            put("description", "Filesystem path to a Plugins directory. This is the only tool that recursively scans plugin folders.")
        }
        putJsonObject("forceRefresh") {
            put("type", "boolean")
            put("description", "Accepted for forward compatibility; current v1 rebuilds the index when called.")
        }
        putJsonObject("maxFiles") {
            put("type", "integer")
            put("description", "Optional safety cap for the number of container files to index; must be >= 1.")
        }
    },
    required = listOf("rootPath"),
)

private fun indexStatusInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("rootPath") {
            put("type", "string")
            put("description", "Filesystem path to a Plugins directory previously passed to index_plugins.")
        }
    },
    required = listOf("rootPath"),
)

private fun searchIndexInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("rootPath") {
            put("type", "string")
            put("description", "Filesystem path to an indexed Plugins directory. This tool never recursively scans; call index_plugins first.")
        }
        putJsonObject("query") {
            put("type", "string")
            put("description", "Case-insensitive substring matched against path, label, exemplar name/type, object class, kind, and TGI.")
        }
        putJsonObject("kindFilter") {
            put("type", "string")
            put("description", "Optional KnownEntryKind enum. Expected one of: ${knownEntryKindValues()}.")
        }
        putJsonObject("objectClass") {
            put("type", "string")
            put("description", "Optional exact object-class hint, for example Lot, Prop, Network, or Transit-enabled Building.")
        }
        putJsonObject("propertyIdHex") {
            put("type", "string")
            put("description", "Optional exemplar property ID as hex; returns indexed exemplars/cohorts containing that property.")
        }
        putJsonObject("limit") {
            put("type", "integer")
            put("description", "Maximum matches to return, 1-500. Default: 50.")
        }
        putJsonObject("offset") {
            put("type", "integer")
            put("description", "Number of matches to skip. Default: 0.")
        }
    },
    required = listOf("rootPath"),
)

private fun readEntryByTgiInputSchema(includeResolveParent: Boolean = false): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file, not a Plugins folder.")
        }
        putJsonObject("tgi") {
            put("type", "string")
            put("description", "Full TGI as hexadecimal type-group-instance, for example 6534284A-00000000-12345678. Provide this or all of type, group, and instance.")
        }
        putJsonObject("type") {
            put("type", "string")
            put("description", "TGI type as hexadecimal, for example 6534284A. Required with group and instance when tgi is omitted.")
        }
        putJsonObject("group") {
            put("type", "string")
            put("description", "TGI group as hexadecimal. Required with type and instance when tgi is omitted.")
        }
        putJsonObject("instance") {
            put("type", "string")
            put("description", "TGI instance as hexadecimal. Required with type and group when tgi is omitted.")
        }
        if (includeResolveParent) {
            putJsonObject("resolveParent") {
                put("type", "boolean")
                put("description", "Resolve parent cohort chain when true. Same-package resolution is always attempted first.")
            }
            putJsonObject("rootPath") {
                put("type", "string")
                put("description", "Optional indexed Plugins directory for cross-package parent lookup. This never scans folders; call index_plugins first.")
            }
        }
    },
    required = listOf("path"),
)

private fun exportEntryByTgiInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file, not a Plugins folder.")
        }
        putJsonObject("tgi") {
            put("type", "string")
            put("description", "Full TGI as hexadecimal type-group-instance, for example 6534284A-00000000-12345678. Provide this or all of type, group, and instance.")
        }
        putJsonObject("type") {
            put("type", "string")
            put("description", "TGI type as hexadecimal, for example 6534284A. Required with group and instance when tgi is omitted.")
        }
        putJsonObject("group") {
            put("type", "string")
            put("description", "TGI group as hexadecimal. Required with type and instance when tgi is omitted.")
        }
        putJsonObject("instance") {
            put("type", "string")
            put("description", "TGI instance as hexadecimal. Required with type and group when tgi is omitted.")
        }
        putJsonObject("outputPath") {
            put("type", "string")
            put("description", "Filesystem path where the exported file will be written.")
        }
    },
    required = listOf("path", "outputPath"),
)

private fun readRawEntryInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file, not a Plugins folder.")
        }
        putJsonObject("tgi") {
            put("type", "string")
        }
        putJsonObject("type") {
            put("type", "string")
        }
        putJsonObject("group") {
            put("type", "string")
        }
        putJsonObject("instance") {
            put("type", "string")
        }
        putJsonObject("maxBytes") {
            put("type", "integer")
            put("description", "Maximum raw bytes to return as base64/hex preview, 1-$MAX_ENTRY_PREVIEW_BYTES. Default: $DEFAULT_RAW_MAX_BYTES.")
        }
    },
    required = listOf("path"),
)

private fun readKeyCfgInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file, not a Plugins folder.")
        }
        putJsonObject("tgi") {
            put("type", "string")
        }
        putJsonObject("type") {
            put("type", "string")
        }
        putJsonObject("group") {
            put("type", "string")
        }
        putJsonObject("instance") {
            put("type", "string")
        }
        putJsonObject("maxBytes") {
            put("type", "integer")
            put("description", "Maximum bytes to inspect, 1-$MAX_ENTRY_PREVIEW_BYTES. Default: $DEFAULT_KEYCFG_MAX_BYTES.")
        }
    },
    required = listOf("path"),
)

private fun readTabBinaryInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file, not a Plugins folder.")
        }
        putJsonObject("tgi") {
            put("type", "string")
        }
        putJsonObject("type") {
            put("type", "string")
        }
        putJsonObject("group") {
            put("type", "string")
        }
        putJsonObject("instance") {
            put("type", "string")
        }
        putJsonObject("maxBytes") {
            put("type", "integer")
            put("description", "Maximum bytes to inspect, 1-$MAX_ENTRY_PREVIEW_BYTES. Default: $DEFAULT_TAB_MAX_BYTES.")
        }
        putJsonObject("maxWords") {
            put("type", "integer")
            put("description", "Maximum decoded 32-bit words to return, 1-$MAX_TAB_WORDS. Default: $DEFAULT_TAB_MAX_WORDS.")
        }
    },
    required = listOf("path"),
)

private fun readFshInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file, not a Plugins folder.")
        }
        putJsonObject("tgi") { put("type", "string") }
        putJsonObject("type") { put("type", "string") }
        putJsonObject("group") { put("type", "string") }
        putJsonObject("instance") { put("type", "string") }
        putJsonObject("previewElementIndex") {
            put("type", "integer")
            put("description", "Optional zero-based FSH element index.")
        }
        putJsonObject("previewImageIndex") {
            put("type", "integer")
            put("description", "Optional zero-based mip/image index inside the selected FSH element.")
        }
    },
    required = listOf("path"),
)

private fun readImageEntryInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file, not a Plugins folder.")
        }
        putJsonObject("tgi") { put("type", "string") }
        putJsonObject("type") { put("type", "string") }
        putJsonObject("group") { put("type", "string") }
        putJsonObject("instance") { put("type", "string") }
        putJsonObject("elementIndex") {
            put("type", "integer")
            put("description", "Optional zero-based FSH element index.")
        }
        putJsonObject("imageIndex") {
            put("type", "integer")
            put("description", "Optional zero-based mip/image index inside the selected FSH element.")
        }
    },
    required = listOf("path"),
)

private fun exportFshPngInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Filesystem path to one DBPF package file, not a Plugins folder.")
        }
        putJsonObject("tgi") { put("type", "string") }
        putJsonObject("type") { put("type", "string") }
        putJsonObject("group") { put("type", "string") }
        putJsonObject("instance") { put("type", "string") }
        putJsonObject("outputPath") {
            put("type", "string")
            put("description", "Filesystem path where the PNG will be written.")
        }
        putJsonObject("elementIndex") {
            put("type", "integer")
            put("description", "Optional zero-based FSH element index.")
        }
        putJsonObject("imageIndex") {
            put("type", "integer")
            put("description", "Optional zero-based mip/image index inside the selected FSH element.")
        }
    },
    required = listOf("path", "outputPath"),
)

private fun describePropertyInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("id") {
            put("type", "string")
            put("description", "Property ID as hexadecimal, for example 0x00000020")
        }
    },
    required = listOf("id"),
)

private fun decodePropertyValueInputSchema(): Tool.Input = Tool.Input(
    properties = buildJsonObject {
        putJsonObject("id") {
            put("type", "string")
            put("description", "Property ID as hexadecimal, for example 0x00000020")
        }
        putJsonObject("values") {
            put("type", "array")
            putJsonObject("items") {
                put("type", "string")
            }
        }
    },
    required = listOf("id", "values"),
)
