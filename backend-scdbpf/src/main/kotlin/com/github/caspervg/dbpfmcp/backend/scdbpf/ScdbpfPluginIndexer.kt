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
import com.github.caspervg.dbpfmcp.semantics.EXEMPLAR_TYPE_PROPERTY_ID
import com.github.caspervg.dbpfmcp.semantics.exemplarTypeLabel
import com.github.caspervg.dbpfmcp.semantics.formatHex32
import com.github.caspervg.dbpfmcp.semantics.kindForType
import com.github.caspervg.dbpfmcp.semantics.objectClassFor
import io.github.memo33.scdbpf.DbpfFile
import io.github.memo33.scdbpf.StreamedEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import scala.jdk.javaapi.CollectionConverters
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

internal class ScdbpfPluginIndexer {
    private val json = Json { ignoreUnknownKeys = true }

    fun indexPlugins(request: IndexPluginsRequest): IndexPluginsResult {
        val root = validateRoot(request.rootPath)
        val maxFiles = request.maxFiles
        if (maxFiles != null && maxFiles <= 0) {
            throw InputError("maxFiles must be > 0")
        }

        val cachePath = indexPath(root)
        val files = containerFiles(root).let { found ->
            if (maxFiles == null) found else found.take(maxFiles)
        }

        // forceRefresh was previously accepted and ignored, so every call rebuilt from scratch.
        if (!request.forceRefresh) {
            val current = currentIndexOrNull(cachePath, files)
            if (current != null) {
                return IndexPluginsResult(
                    rootPath = root.absolutePathString(),
                    cachePath = cachePath.absolutePathString(),
                    fileCount = current.fileCount,
                    indexedFileCount = current.fileCount,
                    entryCount = current.entryCount,
                    skippedFileCount = 0,
                    warningCount = 0,
                    warnings = listOf("Index is already current; pass forceRefresh=true to rebuild it."),
                    builtAtEpochMillis = current.builtAtEpochMillis,
                )
            }
        }

        val warnings = mutableListOf<String>()
        val records = mutableListOf<IndexedEntry>()
        val indexedFiles = mutableListOf<IndexedFile>()
        val builtAt = System.currentTimeMillis()
        var skippedFiles = 0
        var entryCount = 0

        files.forEach { file ->
            var mtime = 0L
            var size = 0L
            try {
                val stat = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes::class.java)
                mtime = stat.lastModifiedTime().toMillis()
                size = stat.size()
                val dbpf = DbpfFile.read(file.toFile(), dbpfHandler) as DbpfFile
                val entries = CollectionConverters.asJava(dbpf.entries()).map { it as StreamedEntry }
                entryCount += entries.size
                entries.forEach { entry ->
                    records += indexRecord(root, file, entry, warnings)
                }
                indexedFiles += IndexedFile(file.absolutePathString(), mtime, size)
            } catch (exception: Exception) {
                skippedFiles += 1
                indexedFiles += IndexedFile(file.absolutePathString(), mtime, size, skipped = true)
                warnings += "Skipped ${file.absolutePathString()}: ${exception.message ?: exception::class.simpleName}"
            }
        }

        val metadata = IndexMetadata(
            rootPath = root.absolutePathString(),
            builtAtEpochMillis = builtAt,
            files = indexedFiles,
            entryCount = entryCount,
        )
        writeIndexAtomically(
            cachePath,
            buildList {
                add(json.encodeToString<IndexRecord>(metadata))
                records.forEach { add(json.encodeToString<IndexRecord>(it)) }
            },
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

        val parsed = readIndexOrNull(cachePath)
            ?: return IndexStatusResult(
                rootPath = root.absolutePathString(),
                cachePath = cachePath.absolutePathString(),
                exists = true,
                fileCount = 0,
                entryCount = 0,
                staleIndexedFileCount = 0,
                missingIndexedFileCount = 0,
                unreadable = true,
                warnings = listOf(staleIndexMessage(root)),
            )

        var stale = 0
        var missing = 0
        parsed.metadata.files.forEach { indexed ->
            val file = Path.of(indexed.path)
            if (!Files.exists(file)) {
                missing += 1
            } else if (fileChanged(indexed)) {
                stale += 1
            }
        }

        return IndexStatusResult(
            rootPath = root.absolutePathString(),
            cachePath = cachePath.absolutePathString(),
            exists = true,
            fileCount = parsed.metadata.files.size,
            entryCount = parsed.entries.size,
            staleIndexedFileCount = stale,
            missingIndexedFileCount = missing,
            builtAtEpochMillis = parsed.metadata.builtAtEpochMillis,
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

        val parsed = readIndexOrNull(cachePath) ?: throw InputError(staleIndexMessage(root))
        val query = request.query?.trim()?.takeIf(String::isNotEmpty)?.lowercase()
        val objectClass = request.objectClass?.trim()?.takeIf(String::isNotEmpty)?.lowercase()
        val matches = parsed.entries.mapNotNull { entry ->
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
        // One read, not three: this used to call indexStatus (a full parse) and then parse the
        // whole file again, once per link of a parent-cohort chain.
        val parsed = readIndexOrNull(cachePath) ?: return IndexedEntryLookup.Unavailable(staleIndexMessage(root))
        if (parsed.metadata.files.any(::fileChanged)) {
            return IndexedEntryLookup.Unavailable(
                "Index is stale for ${root.absolutePathString()}; call index_plugins before resolving cross-package parents.",
            )
        }
        val entry = parsed.entries.firstOrNull { indexed ->
            indexed.tgi == tgi && (allowedKinds.isEmpty() || indexed.kind in allowedKinds)
        } ?: return IndexedEntryLookup.NotFound
        return IndexedEntryLookup.Found(packagePath = entry.packagePath)
    }

    private fun indexRecord(
        root: Path,
        file: Path,
        entry: StreamedEntry,
        warnings: MutableList<String>,
    ): IndexedEntry {
        val tgi = tgiToDomain(entry.tgi())
        val kind = kindForType(tgi.type)
        val hint = if (kind == KnownEntryKind.EXEMPLAR || kind == KnownEntryKind.COHORT) {
            exemplarHint(entry, tgi, warnings)
        } else {
            null
        }
        return IndexedEntry(
            packagePath = file.absolutePathString(),
            relativePath = root.relativize(file).toString(),
            tgi = tgi,
            kind = kind,
            label = entry.tgi().label().takeIf(String::isNotBlank),
            exemplarName = hint?.name,
            exemplarType = hint?.exemplarType,
            objectClass = hint?.objectClass,
            propertyIds = hint?.propertyIds?.map(::formatHex32) ?: emptyList(),
        )
    }

    private fun exemplarHint(
        entry: StreamedEntry,
        tgi: Tgi,
        warnings: MutableList<String>,
    ): IndexedExemplarHint? = try {
        val exemplar = decodeExemplarEntry(entry).content()
        val properties = scalaMapEntries(exemplar.properties()).associate { (id, prop) -> id.toLong() to prop }
        val name = properties[0x20L]?.let(::propertyValues)?.firstOrNull()?.jsonPrimitive?.contentOrNull
        val exemplarType = properties[EXEMPLAR_TYPE_PROPERTY_ID]
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
    } catch (exception: Exception) {
        // Indexed without a name or property IDs, so search_index cannot find it by those.
        // Previously this was swallowed silently, which made the gap invisible.
        warnings += "Indexed ${formatTgi(tgi)} without exemplar details: " +
            (exception.message ?: exception::class.simpleName.orEmpty())
        null
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

    /**
     * Reads the JSONL cache, or returns null when it cannot be used.
     *
     * A cache can be unusable because it was written by an older format version, or because a
     * previous run was interrupted mid-write and left a truncated final line. Neither is an
     * error worth propagating: the caller is told to rebuild. Previously both cases threw an
     * untyped exception on every subsequent call, and the only fix was deleting the file by hand.
     */
    private fun readIndexOrNull(cachePath: Path): ParsedIndex? = try {
        var metadata: IndexMetadata? = null
        val entries = mutableListOf<IndexedEntry>()
        cachePath.readLines().forEach { line ->
            if (line.isNotBlank()) {
                when (val record = json.decodeFromString<IndexRecord>(line)) {
                    is IndexMetadata -> metadata = record
                    is IndexedEntry -> entries += record
                }
            }
        }
        metadata
            ?.takeIf { it.formatVersion == INDEX_FORMAT_VERSION }
            ?.let { ParsedIndex(it, entries) }
    } catch (exception: Exception) {
        null
    }

    /**
     * Writes to a sibling temp file and renames over the target.
     *
     * A direct write that is interrupted leaves a truncated final line, which then fails to parse
     * on every later read. The rename is atomic where the filesystem supports it, so readers see
     * either the old index or the new one.
     */
    private fun writeIndexAtomically(cachePath: Path, lines: List<String>) {
        cachePath.parent.createDirectories()
        val temp = Files.createTempFile(cachePath.parent, cachePath.name, ".tmp")
        try {
            temp.writeLines(lines)
            try {
                Files.move(temp, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, cachePath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: Exception) {
            Files.deleteIfExists(temp)
            throw PackageError("Failed to write index cache ${cachePath.absolutePathString()}", exception)
        }
    }

    /**
     * Returns index statistics only when the cache is readable, covers exactly [files], and every
     * one of those files is unchanged since it was indexed. Any difference — a modified package, a
     * deleted one, or a newly added one — means the caller must rebuild.
     */
    private fun currentIndexOrNull(cachePath: Path, files: List<Path>): IndexFreshness? {
        if (!cachePath.exists()) return null
        val parsed = readIndexOrNull(cachePath) ?: return null
        if (parsed.metadata.files.map(IndexedFile::path).toSet() != files.map(Path::absolutePathString).toSet()) {
            return null
        }
        if (parsed.metadata.files.any(::fileChanged)) return null
        return IndexFreshness(
            fileCount = parsed.metadata.files.size,
            entryCount = parsed.entries.size,
            builtAtEpochMillis = parsed.metadata.builtAtEpochMillis,
        )
    }

    /** True when an indexed package is gone or differs from what was recorded. */
    private fun fileChanged(indexed: IndexedFile): Boolean {
        val file = Path.of(indexed.path)
        if (!Files.exists(file)) return true
        val stat = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes::class.java)
        return stat.lastModifiedTime().toMillis() != indexed.mtimeMillis || stat.size() != indexed.size
    }

    private fun staleIndexMessage(root: Path): String =
        "Index for ${root.absolutePathString()} is missing, unreadable, or was written by an " +
            "older version of dbpf-mcp; call index_plugins to rebuild it."

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

/**
 * Bump when the on-disk record shape changes. A cache written by a different version is treated
 * as absent rather than parsed on a best-effort basis.
 */
internal const val INDEX_FORMAT_VERSION: Int = 1

/**
 * One line of the JSONL cache. `recordType` is the discriminator, so kotlinx handles the dispatch
 * that used to be a hand-written `when` over manually extracted fields.
 */
@Serializable
@JsonClassDiscriminator("recordType")
internal sealed class IndexRecord

@Serializable
@SerialName("metadata")
internal data class IndexMetadata(
    val formatVersion: Int = INDEX_FORMAT_VERSION,
    val rootPath: String,
    val builtAtEpochMillis: Long,
    /**
     * Every package the build considered, including ones that could not be parsed. Recording
     * skipped files here is what lets a rebuild be skipped: a file that yields no entries is
     * otherwise indistinguishable from a file that was never indexed.
     */
    val files: List<IndexedFile>,
    val entryCount: Int,
) : IndexRecord()

@Serializable
internal data class IndexedFile(
    val path: String,
    val mtimeMillis: Long,
    val size: Long,
    val skipped: Boolean = false,
)

@Serializable
@SerialName("entry")
internal data class IndexedEntry(
    val packagePath: String,
    val relativePath: String,
    val tgi: Tgi,
    val kind: KnownEntryKind,
    val label: String? = null,
    val exemplarName: String? = null,
    val exemplarType: String? = null,
    val objectClass: String? = null,
    val propertyIds: List<String> = emptyList(),
) : IndexRecord()

private data class ParsedIndex(
    val metadata: IndexMetadata,
    val entries: List<IndexedEntry>,
)

private data class IndexFreshness(
    val fileCount: Int,
    val entryCount: Int,
    val builtAtEpochMillis: Long,
)
