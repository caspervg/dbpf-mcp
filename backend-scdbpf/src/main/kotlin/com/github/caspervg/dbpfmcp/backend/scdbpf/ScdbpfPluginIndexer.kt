package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.IndexPluginsRequest
import com.github.caspervg.dbpfmcp.core.IndexPluginsResult
import com.github.caspervg.dbpfmcp.core.IndexStatusRequest
import com.github.caspervg.dbpfmcp.core.IndexStatusResult
import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import com.github.caspervg.dbpfmcp.core.PackageError
import com.github.caspervg.dbpfmcp.core.SearchIndexMatch
import com.github.caspervg.dbpfmcp.core.SearchIndexRequest
import com.github.caspervg.dbpfmcp.core.SearchIndexResult
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.semantics.SC4TypeIds
import com.github.caspervg.dbpfmcp.semantics.formatHex32
import com.github.caspervg.dbpfmcp.semantics.kindForType
import io.github.memo33.scdbpf.BufferedEntry
import io.github.memo33.scdbpf.DbpfFile
import io.github.memo33.scdbpf.DbpfType
import io.github.memo33.scdbpf.DbpfProperty
import io.github.memo33.scdbpf.Exemplar
import io.github.memo33.scdbpf.RawEntry
import io.github.memo33.scdbpf.StreamedEntry
import io.github.memo33.scdbpf.Tgi as ScTgi
import io.github.memo33.scdbpf.compat.ExceptionHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import scala.jdk.javaapi.CollectionConverters
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

internal class ScdbpfPluginIndexer {
    private val handler: ExceptionHandler = io.github.memo33.scdbpf.`package`.strategy().throwExceptions()
    private val json = Json { ignoreUnknownKeys = true }

    fun indexPlugins(request: IndexPluginsRequest): IndexPluginsResult {
        val root = validateRoot(request.rootPath)
        val maxFiles = request.maxFiles
        if (maxFiles != null && maxFiles <= 0) {
            throw InputError("maxFiles must be > 0")
        }

        val files = containerFiles(root).let { found ->
            if (maxFiles == null) found else found.take(maxFiles)
        }
        val cachePath = indexPath(root)
        val warnings = mutableListOf<String>()
        val records = mutableListOf<JsonObject>()
        val builtAt = System.currentTimeMillis()
        var skippedFiles = 0
        var entryCount = 0

        files.forEach { file ->
            try {
                val stat = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes::class.java)
                val dbpf = DbpfFile.read(file.toFile(), handler) as DbpfFile
                val entries = CollectionConverters.asJava(dbpf.entries()).map { it as StreamedEntry }
                entryCount += entries.size
                entries.forEach { entry ->
                    records += indexRecord(root, file, stat.lastModifiedTime().toMillis(), stat.size(), entry)
                }
            } catch (exception: Exception) {
                skippedFiles += 1
                warnings += "Skipped ${file.absolutePathString()}: ${exception.message ?: exception::class.simpleName}"
            }
        }

        cachePath.parent.createDirectories()
        cachePath.writeLines(
            buildList {
                add(
                    json.encodeToString(
                        JsonObject.serializer(),
                        buildJsonObject {
                            put("recordType", "metadata")
                            put("rootPath", root.absolutePathString())
                            put("builtAtEpochMillis", builtAt)
                            put("fileCount", files.size)
                            put("entryCount", entryCount)
                        },
                    )
                )
                records.forEach { add(json.encodeToString(JsonObject.serializer(), it)) }
            }
        )

        return IndexPluginsResult(
            rootPath = root.absolutePathString(),
            cachePath = cachePath.absolutePathString(),
            fileCount = files.size,
            indexedFileCount = files.size - skippedFiles,
            entryCount = entryCount,
            skippedFileCount = skippedFiles,
            warningCount = warnings.size,
            warnings = warnings.take(50),
            builtAtEpochMillis = builtAt,
        )
    }

    fun indexStatus(request: IndexStatusRequest): IndexStatusResult {
        val root = validateRoot(request.rootPath)
        val cachePath = indexPath(root)
        if (!cachePath.exists()) {
            return IndexStatusResult(
                rootPath = root.absolutePathString(),
                cachePath = cachePath.absolutePathString(),
                exists = false,
                fileCount = 0,
                entryCount = 0,
                staleIndexedFileCount = 0,
                missingIndexedFileCount = 0,
            )
        }

        val parsed = readIndex(cachePath)
        val fileStats = parsed.entries
            .groupBy { it.packagePath }
            .mapValues { (_, entries) -> entries.first() }
        var stale = 0
        var missing = 0
        fileStats.values.forEach { entry ->
            val file = Path.of(entry.packagePath)
            if (!Files.exists(file)) {
                missing += 1
            } else {
                val stat = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes::class.java)
                if (stat.lastModifiedTime().toMillis() != entry.packageMtimeMillis || stat.size() != entry.packageSize) {
                    stale += 1
                }
            }
        }

        return IndexStatusResult(
            rootPath = root.absolutePathString(),
            cachePath = cachePath.absolutePathString(),
            exists = true,
            fileCount = fileStats.size,
            entryCount = parsed.entries.size,
            staleIndexedFileCount = stale,
            missingIndexedFileCount = missing,
            builtAtEpochMillis = parsed.builtAtEpochMillis,
        )
    }

    fun searchIndex(request: SearchIndexRequest): SearchIndexResult {
        val root = validateRoot(request.rootPath)
        val cachePath = indexPath(root)
        if (!cachePath.exists()) {
            throw InputError("Index not found for ${root.absolutePathString()}; call index_plugins first.")
        }
        val limit = request.limit ?: 50
        val offset = request.offset ?: 0
        if (limit !in 1..500) {
            throw InputError("limit must be between 1 and 500")
        }
        if (offset < 0) {
            throw InputError("offset must be >= 0")
        }

        val query = request.query?.trim()?.takeIf(String::isNotEmpty)?.lowercase()
        val objectClass = request.objectClass?.trim()?.takeIf(String::isNotEmpty)?.lowercase()
        val matches = readIndex(cachePath).entries.mapNotNull { entry ->
            matchEntry(entry, query, request.kindFilter, objectClass, request.propertyId)
        }

        return SearchIndexResult(
            rootPath = root.absolutePathString(),
            cachePath = cachePath.absolutePathString(),
            totalMatches = matches.size,
            offset = offset,
            limit = limit,
            matches = matches.drop(offset).take(limit),
        )
    }

    fun findIndexedEntry(
        rootPath: String,
        tgi: Tgi,
        allowedKinds: Set<KnownEntryKind> = emptySet(),
    ): IndexedEntryLookup {
        val root = validateRoot(rootPath)
        val cachePath = indexPath(root)
        if (!cachePath.exists()) {
            return IndexedEntryLookup.Unavailable("Index not found for ${root.absolutePathString()}; call index_plugins first.")
        }
        val status = indexStatus(IndexStatusRequest(root.absolutePathString()))
        if (status.staleIndexedFileCount > 0 || status.missingIndexedFileCount > 0) {
            return IndexedEntryLookup.Unavailable(
                "Index is stale for ${root.absolutePathString()}; call index_plugins before resolving cross-package parents.",
            )
        }
        val entry = readIndex(cachePath).entries.firstOrNull { indexed ->
            indexed.tgi == tgi && (allowedKinds.isEmpty() || indexed.kind in allowedKinds)
        } ?: return IndexedEntryLookup.NotFound
        return IndexedEntryLookup.Found(packagePath = entry.packagePath)
    }

    private fun indexRecord(
        root: Path,
        file: Path,
        mtimeMillis: Long,
        size: Long,
        entry: StreamedEntry,
    ): JsonObject {
        val tgi = tgiToDomain(entry.tgi())
        val kind = kindForType(tgi.type)
        val hint = if (kind == KnownEntryKind.EXEMPLAR || kind == KnownEntryKind.COHORT) {
            exemplarHint(entry)
        } else {
            null
        }
        val rawEntry = runCatching { entry.toRawEntry(handler) as RawEntry }.getOrNull()
        return buildJsonObject {
            put("recordType", "entry")
            put("rootPath", root.absolutePathString())
            put("packagePath", file.absolutePathString())
            put("relativePath", root.relativize(file).toString())
            put("packageMtimeMillis", mtimeMillis)
            put("packageSize", size)
            put("type", formatHex32(tgi.type))
            put("group", formatHex32(tgi.group))
            put("instance", formatHex32(tgi.instance))
            put("kind", kind.name)
            put("label", entry.tgi().label().takeIf(String::isNotBlank)?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
            put("size", entry.size().toLong())
            put("compressed", rawEntry?.compressed()?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
            if (hint != null) {
                put("exemplarName", hint.name?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                put("exemplarType", hint.exemplarType?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                put("objectClass", hint.objectClass)
                putJsonArray("propertyIds") {
                    hint.propertyIds.forEach { add(JsonPrimitive(formatHex32(it))) }
                }
            }
        }
    }

    private fun exemplarHint(entry: StreamedEntry): IndexedExemplarHint? = runCatching {
        val exemplar = decodeExemplarEntry(entry).content()
        val properties = CollectionConverters.asJava(exemplar.properties()).entries.associate { (id, prop) ->
            id.toLong() to prop
        }
        val name = properties[0x20L]?.let(::propertyValues)?.firstOrNull()?.jsonPrimitive?.contentOrNull
        val exemplarType = properties[0x10L]
            ?.let(::propertyValues)
            ?.firstOrNull()
            ?.jsonPrimitive
            ?.longOrNull
            ?.let(::exemplarTypeLabel)
        val propertyIds = properties.keys.sorted()
        IndexedExemplarHint(
            name = name,
            exemplarType = exemplarType,
            objectClass = objectClassFor(exemplarType, propertyIds),
            propertyIds = propertyIds,
        )
    }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun decodeExemplarEntry(entry: StreamedEntry): BufferedEntry<Exemplar> {
        val buffered = entry.toBufferedEntry(handler) as BufferedEntry<DbpfType>
        return buffered.convert(handler, Exemplar.converter()) as BufferedEntry<Exemplar>
    }

    private fun propertyValues(propertyList: DbpfProperty.PropertyList<*>): List<kotlinx.serialization.json.JsonElement> = when (propertyList) {
        is DbpfProperty.Single<*> -> listOf(valueToJson(propertyList.value()))
        is DbpfProperty.Multi<*> -> CollectionConverters.asJava(propertyList.values()).map(::valueToJson)
        else -> listOf(JsonPrimitive(propertyList.toString()))
    }

    private fun valueToJson(value: Any?): kotlinx.serialization.json.JsonElement = when (value) {
        null -> kotlinx.serialization.json.JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value.toLong())
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value.toDouble())
        is Double -> JsonPrimitive(value)
        is io.github.memo33.passera.unsigned.UInt -> JsonPrimitive(value.toLong())
        is io.github.memo33.passera.unsigned.UShort -> JsonPrimitive(value.toInt())
        else -> JsonPrimitive(value.toString())
    }

    private fun matchEntry(
        entry: IndexedEntry,
        query: String?,
        kindFilter: KnownEntryKind?,
        objectClass: String?,
        propertyId: Long?,
    ): SearchIndexMatch? {
        if (kindFilter != null && entry.kind != kindFilter) return null
        if (objectClass != null && entry.objectClass?.lowercase() != objectClass) return null
        if (propertyId != null && formatHex32(propertyId) !in entry.propertyIds) return null
        val fields = buildList {
            add("packagePath" to entry.packagePath)
            add("relativePath" to entry.relativePath)
            entry.label?.let { add("label" to it) }
            entry.exemplarName?.let { add("exemplarName" to it) }
            entry.exemplarType?.let { add("exemplarType" to it) }
            entry.objectClass?.let { add("objectClass" to it) }
            add("kind" to entry.kind.name)
            add("tgi" to formatTgi(entry.tgi))
        }
        val matched = if (query == null) {
            "filter" to "Matched filters"
        } else {
            fields.firstOrNull { (_, value) -> value.lowercase().contains(query) } ?: return null
        }
        return SearchIndexMatch(
            packagePath = entry.packagePath,
            tgi = entry.tgi,
            kind = entry.kind,
            label = entry.label,
            exemplarName = entry.exemplarName,
            exemplarType = entry.exemplarType,
            objectClass = entry.objectClass,
            matchedField = matched.first,
            context = matched.second,
        )
    }

    private fun readIndex(cachePath: Path): ParsedIndex {
        var builtAt: Long? = null
        val entries = mutableListOf<IndexedEntry>()
        cachePath.readLines().forEach { line ->
            if (line.isBlank()) return@forEach
            val obj = json.parseToJsonElement(line).jsonObject
            when (obj.string("recordType")) {
                "metadata" -> builtAt = obj.long("builtAtEpochMillis")
                "entry" -> entries += IndexedEntry(
                    packagePath = obj.requiredString("packagePath"),
                    relativePath = obj.requiredString("relativePath"),
                    packageMtimeMillis = obj.long("packageMtimeMillis") ?: 0L,
                    packageSize = obj.long("packageSize") ?: 0L,
                    tgi = Tgi(
                        type = parseHex(obj.requiredString("type")),
                        group = parseHex(obj.requiredString("group")),
                        instance = parseHex(obj.requiredString("instance")),
                    ),
                    kind = KnownEntryKind.valueOf(obj.requiredString("kind")),
                    label = obj.string("label"),
                    exemplarName = obj.string("exemplarName"),
                    exemplarType = obj.string("exemplarType"),
                    objectClass = obj.string("objectClass"),
                    propertyIds = obj["propertyIds"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                )
            }
        }
        return ParsedIndex(builtAt, entries)
    }

    private fun containerFiles(root: Path): List<Path> =
        Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { isContainerFile(it) }
                .sorted(compareBy<Path> { root.relativize(it).toString().lowercase() })
                .toList()
        }

    private fun isContainerFile(path: Path): Boolean =
        path.name.lowercase().let {
            it.endsWith(".dat") ||
                it.endsWith(".sc4lot") ||
                it.endsWith(".sc4model") ||
                it.endsWith(".sc4desc")
        }

    private fun validateRoot(rootPath: String): Path {
        val root = Path.of(rootPath).toAbsolutePath().normalize()
        if (!root.exists()) {
            throw PackageError("Plugins root not found: ${root.absolutePathString()}")
        }
        if (!root.isDirectory()) {
            throw InputError("rootPath must be a directory; use inspect_package for single DBPF files.")
        }
        return root
    }

    private fun indexPath(root: Path): Path {
        val cacheRoot = System.getenv("DBPF_MCP_INDEX_DIR")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".cache", "dbpf-mcp", "indexes")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(root.absolutePathString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return cacheRoot.resolve("$digest.jsonl")
    }

    private fun tgiToDomain(tgi: ScTgi): Tgi = Tgi(
        type = unsignedInt(tgi.tid()),
        group = unsignedInt(tgi.gid()),
        instance = unsignedInt(tgi.iid()),
    )

    private fun unsignedInt(value: Any): Long = (value as Number).toLong() and 0xFFFF_FFFFL

    private fun formatTgi(tgi: Tgi): String =
        "${formatHex32(tgi.type)}-${formatHex32(tgi.group)}-${formatHex32(tgi.instance)}"

    private fun parseHex(value: String): Long = value.toULong(16).toLong()

    private fun exemplarTypeLabel(value: Long): String? = when (value) {
        0x0000000AL -> "Lot Configuration"
        0x0000000BL -> "Network"
        0x0000001EL -> "Prop"
        0x00000021L -> "Network Lot (T21)"
        else -> null
    }

    private fun objectClassFor(exemplarType: String?, propertyIds: List<Long>): String {
        if (propertyIds.any { it in 0x88EDC900L..0x88EDCDFFL }) return "Lot"
        if (propertyIds.any { it in setOf(0xE90E25A1L, 0xE90E25A2L, 0xE90E25A3L) }) return "Transit-enabled Building"
        return exemplarType ?: "Exemplar"
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.requiredString(name: String): String =
        string(name) ?: throw PackageError("Corrupt index entry missing $name")

    private fun JsonObject.long(name: String): Long? =
        this[name]?.jsonPrimitive?.longOrNull
}

private data class IndexedExemplarHint(
    val name: String?,
    val exemplarType: String?,
    val objectClass: String,
    val propertyIds: List<Long>,
)

internal sealed interface IndexedEntryLookup {
    data class Found(val packagePath: String) : IndexedEntryLookup
    data class Unavailable(val warning: String) : IndexedEntryLookup
    data object NotFound : IndexedEntryLookup
}

private data class IndexedEntry(
    val packagePath: String,
    val relativePath: String,
    val packageMtimeMillis: Long,
    val packageSize: Long,
    val tgi: Tgi,
    val kind: KnownEntryKind,
    val label: String?,
    val exemplarName: String?,
    val exemplarType: String?,
    val objectClass: String?,
    val propertyIds: List<String>,
)

private data class ParsedIndex(
    val builtAtEpochMillis: Long?,
    val entries: List<IndexedEntry>,
)
