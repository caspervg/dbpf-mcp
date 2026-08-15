package com.github.caspervg.dbpfmcp.server

import com.github.caspervg.dbpfmcp.backend.scdbpf.ScdbpfAdapter
import com.github.caspervg.dbpfmcp.core.DbpfException
import com.github.caspervg.dbpfmcp.core.ExplainEntryRequest
import com.github.caspervg.dbpfmcp.core.ExportCohortTextRequest
import com.github.caspervg.dbpfmcp.core.ExportExemplarTextRequest
import com.github.caspervg.dbpfmcp.core.ExportFshPngRequest
import com.github.caspervg.dbpfmcp.core.ExportSC4PathsJsonRequest
import com.github.caspervg.dbpfmcp.core.ExportSC4PathsTextRequest
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
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun main(): Unit = runBlocking {
    val adapter = ScdbpfAdapter()
    val closed = CompletableDeferred<Unit>()
    val server = Server(
        serverInfo = Implementation("dbpf-mcp", "0.1.0"),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    ) {
        // ----- Package inspection -------------------------------------------------------------

        readTool<ListEntriesArgs, _>(
            name = "list_entries",
            title = "List DBPF Entries",
            description = "List the entries of one DBPF package by TGI, with kind, size, " +
                "compression, and label. Results are paged; matchCount and truncated tell you " +
                "whether more entries matched than were returned.",
        ) { adapter.listEntries(it.toRequest()) }

        readTool<PackagePathArgs, _>(
            name = "summarize_package",
            title = "Summarize Package",
            description = "Summarize one DBPF package by entry kind, compression, and common labels.",
        ) { adapter.summarizePackage(it.toSummarizeRequest()) }

        readTool<InspectPackageArgs, _>(
            name = "inspect_package",
            title = "Inspect Package",
            description = "Inspect one DBPF package and return bounded, agent-friendly hints: " +
                "notable entries, SC4 object classes, and which tool to reach for next. Does not " +
                "scan folders.",
        ) { adapter.inspectPackage(it.toRequest()) }

        readTool<EntryByTgiArgs, _>(
            name = "explain_entry",
            title = "Explain Entry",
            description = "Explain one entry in prose: what it is, its important fields, and how " +
                "it relates to other entries.",
        ) { adapter.explainEntry(ExplainEntryRequest(path = it.path, tgi = it.resolveTgi())) }

        // ----- Plugins index ------------------------------------------------------------------

        writeTool<IndexPluginsArgs, _>(
            name = "index_plugins",
            title = "Index Plugins Folder",
            description = "Build a persistent metadata index for a Plugins folder. This is the " +
                "only tool that scans folders recursively. Skipped when the existing index is " +
                "still current unless forceRefresh is set.",
        ) { adapter.indexPlugins(it.toRequest()) }

        readTool<IndexStatusArgs, _>(
            name = "index_status",
            title = "Index Status",
            description = "Report whether a Plugins index exists and whether any indexed package " +
                "has changed or gone missing since it was built.",
        ) { adapter.indexStatus(it.toRequest()) }

        readTool<SearchIndexArgs, _>(
            name = "search_index",
            title = "Search Plugins Index",
            description = "Search a Plugins index by TGI, kind, exemplar name, object class, or " +
                "property ID. Never scans the folder; run index_plugins first.",
        ) { adapter.searchIndex(it.toRequest()) }

        // ----- Exemplars and cohorts ----------------------------------------------------------

        readTool<ExemplarByTgiArgs, _>(
            name = "read_exemplar",
            title = "Read Exemplar",
            description = "Decode one exemplar into semantic JSON: property names, declared and " +
                "stored types, decoded values, and resource keys. Use read_exemplar_text for the " +
                "canonical text form instead.",
        ) {
            adapter.readExemplar(
                ReadExemplarRequest(
                    path = it.path,
                    tgi = it.resolveTgi(),
                    resolveParent = it.resolveParent,
                    rootPath = it.rootPath,
                ),
            )
        }

        readTool<ExemplarByTgiArgs, _>(
            name = "read_cohort",
            title = "Read Cohort",
            description = "Decode one cohort into semantic JSON, the same shape read_exemplar returns.",
        ) {
            adapter.readCohort(
                ReadCohortRequest(
                    path = it.path,
                    tgi = it.resolveTgi(),
                    resolveParent = it.resolveParent,
                    rootPath = it.rootPath,
                ),
            )
        }

        readTool<EntryByTgiArgs, _>(
            name = "read_exemplar_text",
            title = "Read Exemplar As Text",
            description = "Render one exemplar as canonical SC4 text-exemplar syntax, returned in " +
                "the response rather than written to disk.",
        ) { adapter.readExemplarText(ReadExemplarTextRequest(path = it.path, tgi = it.resolveTgi())) }

        readTool<EntryByTgiArgs, _>(
            name = "read_cohort_text",
            title = "Read Cohort As Text",
            description = "Render one cohort as canonical SC4 text-exemplar syntax.",
        ) { adapter.readCohortText(ReadCohortTextRequest(path = it.path, tgi = it.resolveTgi())) }

        writeTool<ExportEntryByTgiArgs, _>(
            name = "export_exemplar_text",
            title = "Export Exemplar Text",
            description = "Write one exemplar to disk as canonical SC4 text-exemplar syntax.",
        ) {
            adapter.exportExemplarText(
                ExportExemplarTextRequest(path = it.path, tgi = it.resolveTgi(), outputPath = it.outputPath),
            )
        }

        writeTool<ExportEntryByTgiArgs, _>(
            name = "export_cohort_text",
            title = "Export Cohort Text",
            description = "Write one cohort to disk as canonical SC4 text-exemplar syntax.",
        ) {
            adapter.exportCohortText(
                ExportCohortTextRequest(path = it.path, tgi = it.resolveTgi(), outputPath = it.outputPath),
            )
        }

        // ----- Other readers ------------------------------------------------------------------

        readTool<EntryByTgiArgs, _>(
            name = "read_ltext",
            title = "Read LTEXT",
            description = "Decode one LTEXT (localizable text) entry to its string value.",
        ) { adapter.readLText(ReadLTextRequest(path = it.path, tgi = it.resolveTgi())) }

        readTool<EntryByTgiArgs, _>(
            name = "read_sc4paths",
            title = "Read SC4PATHS",
            description = "Decode one SC4PATHS entry into structured path and stop-path records " +
                "with their coordinates.",
        ) { adapter.readSC4Paths(ReadSC4PathsRequest(path = it.path, tgi = it.resolveTgi())) }

        readTool<EntryByTgiArgs, _>(
            name = "read_sc4paths_text",
            title = "Read SC4PATHS As Text",
            description = "Render one SC4PATHS entry as canonical path text.",
        ) { adapter.readSC4PathsText(ReadSC4PathsRequest(path = it.path, tgi = it.resolveTgi())) }

        writeTool<ExportEntryByTgiArgs, _>(
            name = "export_sc4paths_text",
            title = "Export SC4PATHS Text",
            description = "Write one SC4PATHS entry to disk as canonical path text.",
        ) {
            adapter.exportSC4PathsText(
                ExportSC4PathsTextRequest(path = it.path, tgi = it.resolveTgi(), outputPath = it.outputPath),
            )
        }

        writeTool<ExportEntryByTgiArgs, _>(
            name = "export_sc4paths_json",
            title = "Export SC4PATHS JSON",
            description = "Write one SC4PATHS entry to disk as JSON.",
        ) {
            adapter.exportSC4PathsJson(
                ExportSC4PathsJsonRequest(path = it.path, tgi = it.resolveTgi(), outputPath = it.outputPath),
            )
        }

        readTool<EntryByTgiArgs, _>(
            name = "read_s3d",
            title = "Read S3D Model",
            description = "Report S3D model metadata: mesh group summaries, materials, and " +
                "animation metadata. Does not export geometry.",
        ) { adapter.readS3d(ReadS3dRequest(path = it.path, tgi = it.resolveTgi())) }

        readTool<ReadFshArgs, _>(
            name = "read_fsh",
            title = "Read FSH Texture",
            description = "Report FSH texture metadata, and optionally a decoded PNG preview of " +
                "one element and mip level.",
        ) {
            adapter.readFsh(
                ReadFshRequest(
                    path = it.path,
                    tgi = it.resolveTgi(),
                    previewElementIndex = it.previewElementIndex,
                    previewImageIndex = it.previewImageIndex,
                ),
            )
        }

        readTool<ImageEntryArgs, _>(
            name = "read_image_entry",
            title = "Read Image Entry",
            description = "Return a PNG-ready base64 image for a native PNG entry or a decoded " +
                "FSH bitmap.",
        ) {
            adapter.readImageEntry(
                ReadImageEntryRequest(
                    path = it.path,
                    tgi = it.resolveTgi(),
                    elementIndex = it.elementIndex,
                    imageIndex = it.imageIndex,
                ),
            )
        }

        writeTool<ExportFshPngArgs, _>(
            name = "export_fsh_png",
            title = "Export FSH Bitmap As PNG",
            description = "Write one FSH bitmap to disk as a PNG file.",
        ) {
            adapter.exportFshPng(
                ExportFshPngRequest(
                    path = it.path,
                    tgi = it.resolveTgi(),
                    outputPath = it.outputPath,
                    elementIndex = it.elementIndex,
                    imageIndex = it.imageIndex,
                ),
            )
        }

        readTool<RawEntryArgs, _>(
            name = "read_raw_entry",
            title = "Read Raw Entry",
            description = "Return an entry's stored bytes as base64, hex, and a UTF-8 preview, " +
                "with no format decoding. The compressed flag reports whether the stored payload " +
                "is QFS-compressed.",
        ) {
            adapter.readRawEntry(
                ReadRawEntryRequest(path = it.path, tgi = it.resolveTgi(), maxBytes = it.maxBytes),
            )
        }

        readTool<KeyCfgArgs, _>(
            name = "read_keycfg",
            title = "Read KEYCFG (Experimental)",
            description = "Heuristic decoder for KEYCFG/TAB-like text resources. Experimental: it " +
                "recovers text fragments and may not reconstruct complete shortcut records.",
        ) {
            adapter.readKeyCfg(
                ReadKeyCfgRequest(path = it.path, tgi = it.resolveTgi(), maxBytes = it.maxBytes),
            )
        }

        readTool<TabBinaryArgs, _>(
            name = "read_tab_binary",
            title = "Read TAB Binary (Experimental)",
            description = "Structural probe for compiled TAB resources. Experimental: it reports " +
                "little-endian words and chunks, not a semantic TAB model.",
        ) {
            adapter.readTabBinary(
                ReadTabBinaryRequest(
                    path = it.path,
                    tgi = it.resolveTgi(),
                    maxBytes = it.maxBytes,
                    maxWords = it.maxWords,
                ),
            )
        }

        // ----- Network INI --------------------------------------------------------------------

        readTool<ReadIniArgs, _>(
            name = "read_ini",
            title = "Read Network INI",
            description = "Read a Network INI resource stored in a DBPF entry, decompressing it " +
                "if needed.",
        ) { adapter.readIni(it.toRequest()) }

        writeTool<WriteIniArgs, _>(
            name = "write_ini",
            title = "Write Network INI",
            description = "Install exact Network INI text at a TGI in a new or existing package. " +
                "The text is stored verbatim.",
        ) { adapter.writeIni(it.toRequest()) }

        // ----- Property registry and QFS ------------------------------------------------------

        readTool<DescribePropertyArgs, _>(
            name = "describe_property",
            title = "Describe Property",
            description = "Look up a property ID in the bundled SC4 property registry: name, " +
                "type, description, and group.",
        ) { adapter.describeProperty(it.toRequest()) }

        readTool<DecodePropertyValueArgs, _>(
            name = "decode_property_value",
            title = "Decode Property Value",
            description = "Interpret a list of raw values for one property ID, including " +
                "resource-key triplets and known enumerations.",
        ) { adapter.decodePropertyValue(it.toRequest()) }

        readTool<DecodeQfsArgs, _>(
            name = "decode_qfs",
            title = "Decode QFS Stream",
            description = "Decode a standalone base64 QFS/RefPack stream. Entry payloads read " +
                "through the other tools are already decompressed.",
        ) { adapter.decodeQfs(it.toRequest()) }

        // ----- Write tools --------------------------------------------------------------------

        writeTool<WriteExemplarsArgs, _>(
            name = "write_exemplars",
            title = "Write Exemplars",
            description = "Create or patch a DBPF package with exemplar and cohort entries. " +
                "Property types are inferred from the bundled registry unless declared.",
        ) { adapter.writeExemplars(it.toRequest()) }

        writeTool<WriteLTextArgs, _>(
            name = "write_ltext",
            title = "Write LTEXT",
            description = "Create or patch a DBPF package with LTEXT entries, encoded as UTF-16.",
        ) { adapter.writeLText(it.toRequest()) }

        writeTool<WriteFshArgs, _>(
            name = "write_fsh",
            title = "Write FSH Textures",
            description = "Create or patch a DBPF package with FSH texture entries encoded from " +
                "PNG images. Mip levels must be supplied pre-downscaled.",
        ) { adapter.writeFsh(it.toRequest()) }

        writeTool<WriteRawEntriesArgs, _>(
            name = "write_raw_entries",
            title = "Write Raw Entries",
            description = "Write arbitrary bytes to any TGI with no format encoding, for entry " +
                "kinds without a dedicated writer (KEYCFG, TAB, RUL, EFFDIR, PNG).",
        ) { adapter.writeRawEntries(it.toRequest()) }
    }

    server.onClose { closed.complete(Unit) }
    val transport = StdioServerTransport(
        System.`in`.asSource().buffered(),
        System.out.asSink().buffered(),
    )
    server.createSession(transport)
    closed.await()
}

/**
 * Registers a tool whose input and output schemas are generated from [A] and [R].
 *
 * Both schemas, the argument decoding, and the result encoding come from the same `@Serializable`
 * types, so a tool cannot advertise a shape it does not accept.
 */
private inline fun <reified A, reified R> Server.dbpfTool(
    name: String,
    title: String,
    description: String,
    annotations: ToolAnnotations,
    crossinline handler: (A) -> R,
) {
    addTool(
        name = name,
        description = description,
        inputSchema = ToolSchemas.input<A>(),
        title = title,
        outputSchema = ToolSchemas.output<R>(),
        toolAnnotations = annotations,
    ) { request ->
        try {
            val arguments = ServerJson.decodeArguments<A>(request.arguments)
            successResult(ServerJson.encodeResult(handler(arguments)))
        } catch (exception: Exception) {
            errorResult(name, exception)
        }
    }
}

private inline fun <reified A, reified R> Server.readTool(
    name: String,
    title: String,
    description: String,
    crossinline handler: (A) -> R,
) = dbpfTool<A, R>(name, title, description, readOnlyAnnotations(title), handler)

/**
 * A tool that writes to the filesystem.
 *
 * `destructiveHint` is true because every write tool can replace an existing file: the DBPF
 * writers do so on `overwrite`, and the `export_*` tools always do. Clients use this hint to
 * decide what to auto-approve, so claiming otherwise would be wrong.
 */
private inline fun <reified A, reified R> Server.writeTool(
    name: String,
    title: String,
    description: String,
    crossinline handler: (A) -> R,
) = dbpfTool<A, R>(name, title, description, writeAnnotations(title), handler)

private fun readOnlyAnnotations(title: String) = ToolAnnotations(
    title = title,
    readOnlyHint = true,
    destructiveHint = false,
    idempotentHint = true,
    openWorldHint = false,
)

private fun writeAnnotations(title: String) = ToolAnnotations(
    title = title,
    readOnlyHint = false,
    destructiveHint = true,
    idempotentHint = true,
    openWorldHint = false,
)

private fun successResult(payload: JsonObject) = CallToolResult(
    content = listOf(TextContent(ServerJson.encode.encodeToString(JsonObject.serializer(), payload))),
    structuredContent = payload,
    isError = false,
)

/**
 * Reports a failure.
 *
 * `structuredContent` is deliberately omitted: the tool declares an output schema, and an error
 * payload does not conform to it. The full stack trace goes to stderr — never stdout, which
 * carries the MCP transport — because the flattened message alone has repeatedly been too little
 * to diagnose anything.
 */
private fun errorResult(toolName: String, exception: Exception): CallToolResult {
    val message = when (exception) {
        is DbpfException -> exception.message ?: exception::class.simpleName.orEmpty()
        else -> exception.message ?: "Unexpected error (${exception::class.simpleName})"
    }
    if (exception !is DbpfException) {
        System.err.println("dbpf-mcp: unexpected failure in tool '$toolName'")
        exception.printStackTrace(System.err)
    }
    return CallToolResult(
        content = listOf(
            TextContent(
                ServerJson.encode.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject { put("error", message) },
                ),
            ),
        ),
        isError = true,
    )
}
