package com.github.caspervg.dbpfmcp.server

import com.github.caspervg.dbpfmcp.core.DecodePropertyValueRequest
import com.github.caspervg.dbpfmcp.core.DecodeQfsRequest
import com.github.caspervg.dbpfmcp.core.DescribePropertyRequest
import com.github.caspervg.dbpfmcp.core.ExemplarPropertyInput
import com.github.caspervg.dbpfmcp.core.ExemplarWriteEntry
import com.github.caspervg.dbpfmcp.core.FshElementInput
import com.github.caspervg.dbpfmcp.core.FshWriteEntry
import com.github.caspervg.dbpfmcp.core.IndexPluginsRequest
import com.github.caspervg.dbpfmcp.core.IndexStatusRequest
import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.InspectPackageRequest
import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import com.github.caspervg.dbpfmcp.core.LTextWriteEntry
import com.github.caspervg.dbpfmcp.core.ListEntriesRequest
import com.github.caspervg.dbpfmcp.core.RawWriteEntry
import com.github.caspervg.dbpfmcp.core.ReadIniRequest
import com.github.caspervg.dbpfmcp.core.SearchIndexRequest
import com.github.caspervg.dbpfmcp.core.SummarizePackageRequest
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.core.WriteExemplarsRequest
import com.github.caspervg.dbpfmcp.core.WriteFshRequest
import com.github.caspervg.dbpfmcp.core.WriteIniRequest
import com.github.caspervg.dbpfmcp.core.WriteLTextRequest
import com.github.caspervg.dbpfmcp.core.WriteRawEntriesRequest
import com.github.caspervg.dbpfmcp.core.parseHex
import com.github.caspervg.dbpfmcp.semantics.parseTgi
import com.xemantic.ai.tool.schema.meta.Description
import com.xemantic.ai.tool.schema.meta.MaxInt
import com.xemantic.ai.tool.schema.meta.MinInt
import com.xemantic.ai.tool.schema.meta.Pattern
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The wire shape of each tool's arguments.
 *
 * These are deliberately separate from the domain request types in `core-api`: the wire accepts a
 * TGI either as one `"6534284A-A8434037-0C006800"` string or as three hex fields, and it takes
 * every identifier as hex text, whereas the domain works in parsed [Tgi] values and `Long`s.
 * Keeping the two apart means the either/or rule is stated once here rather than re-derived in
 * every tool, and it lets the JSON Schema for each tool be generated from the type the decoder
 * actually uses — so a documented constraint cannot drift from the enforced one.
 *
 * Bounds appear twice on purpose: as `@MinInt`/`@MaxInt` so they reach the published schema, and
 * as a check in `init` so they are still enforced for clients that do not validate.
 */

internal const val DEFAULT_LIST_LIMIT = 200
internal const val MAX_LIST_LIMIT = 1_000
internal const val DEFAULT_SEARCH_LIMIT = 50
internal const val MAX_SEARCH_LIMIT = 500
internal const val DEFAULT_RAW_MAX_BYTES = 4_096
internal const val DEFAULT_KEYCFG_MAX_BYTES = 65_536
internal const val DEFAULT_TAB_MAX_BYTES = 4_096
internal const val DEFAULT_TAB_MAX_WORDS = 128
internal const val MAX_ENTRY_PREVIEW_BYTES = 262_144
internal const val MAX_TAB_WORDS = 4_096

private const val TGI_TRIPLE_DESCRIPTION =
    "Full TGI as one string, for example \"6534284A-A8434037-0C006800\". Parts may be separated " +
        "by '-', ':' or '/'. Provide either this or all three of type, group, and instance."
private const val HEX32_PATTERN = "^(0[xX])?[0-9a-fA-F]{1,8}$"

/** The two accepted spellings of a TGI argument, shared by every read-by-TGI tool. */
internal interface TgiArguments {
    val tgi: String?
    val type: String?
    val group: String?
    val instance: String?
}

/** Resolves the either/or TGI contract, or explains precisely what is missing. */
internal fun TgiArguments.resolveTgi(): Tgi {
    tgi?.let { return parseTgi(it) }
    val missing = listOf("type" to type, "group" to group, "instance" to instance)
        .filter { it.second == null }
        .map { it.first }
    if (missing.isNotEmpty()) {
        throw InputError(
            "Missing TGI arguments: provide either tgi or all of type, group, and instance; " +
                "missing ${missing.joinToString(", ")}",
        )
    }
    return Tgi(
        type = parseHex(type!!, "type"),
        group = parseHex(group!!, "group"),
        instance = parseHex(instance!!, "instance"),
    )
}

private fun Int?.requireInRange(name: String, min: Int, max: Int): Int? {
    if (this != null && (this < min || this > max)) {
        throw InputError("$name must be between $min and $max")
    }
    return this
}

// ---------------------------------------------------------------------------------------------
// Package inspection
// ---------------------------------------------------------------------------------------------

@Serializable
internal data class ListEntriesArgs(
    @Description("Absolute path to one DBPF package file (.dat, .SC4Lot, .SC4Model, .SC4Desc).")
    val path: String,
    @Description("Maximum entries to return in this page.")
    @MinInt(0)
    @MaxInt(MAX_LIST_LIMIT.toLong())
    val limit: Int? = DEFAULT_LIST_LIMIT,
    @Description("Number of matching entries to skip before this page.")
    @MinInt(0)
    val offset: Int? = null,
    @Description("Keep only entries whose type ID equals this hex value, for example \"6534284A\".")
    @Pattern(regex = HEX32_PATTERN)
    val typeFilterHex: String? = null,
    @Description("Keep only entries whose group ID equals this hex value.")
    @Pattern(regex = HEX32_PATTERN)
    val groupFilterHex: String? = null,
    @Description("Keep only entries of this resource kind.")
    val kindFilter: KnownEntryKind? = null,
    @Description("Keep only entries whose label contains this text, case-insensitively.")
    val labelContains: String? = null,
) {
    init {
        limit.requireInRange("limit", 0, MAX_LIST_LIMIT)
        offset.requireInRange("offset", 0, Int.MAX_VALUE)
    }

    fun toRequest() = ListEntriesRequest(
        path = path,
        limit = limit,
        offset = offset,
        typeFilter = typeFilterHex?.let { parseHex(it, "typeFilterHex") },
        groupFilter = groupFilterHex?.let { parseHex(it, "groupFilterHex") },
        kindFilter = kindFilter,
        labelContains = labelContains,
    )
}

@Serializable
internal data class PackagePathArgs(
    @Description("Absolute path to one DBPF package file.")
    val path: String,
) {
    fun toSummarizeRequest() = SummarizePackageRequest(path = path)
}

@Serializable
internal data class InspectPackageArgs(
    @Description("Absolute path to one DBPF package file.")
    val path: String,
    @Description("Maximum notable entries to report.")
    @MinInt(1)
    @MaxInt(200)
    val maxNotableEntries: Int? = null,
    @Description("Maximum SC4 object hints to report.")
    @MinInt(1)
    @MaxInt(200)
    val maxObjectHints: Int? = null,
) {
    init {
        maxNotableEntries.requireInRange("maxNotableEntries", 1, 200)
        maxObjectHints.requireInRange("maxObjectHints", 1, 200)
    }

    fun toRequest() = InspectPackageRequest(
        path = path,
        maxNotableEntries = maxNotableEntries,
        maxObjectHints = maxObjectHints,
    )
}

// ---------------------------------------------------------------------------------------------
// Plugins index
// ---------------------------------------------------------------------------------------------

@Serializable
internal data class IndexPluginsArgs(
    @Description("Absolute path to a Plugins folder. This is the only tool that scans folders recursively.")
    val rootPath: String,
    @Description("Rebuild even when the existing index is still current for every file in the folder.")
    val forceRefresh: Boolean = false,
    @Description("Stop after indexing this many package files.")
    @MinInt(1)
    val maxFiles: Int? = null,
) {
    init {
        maxFiles.requireInRange("maxFiles", 1, Int.MAX_VALUE)
    }

    fun toRequest() = IndexPluginsRequest(rootPath = rootPath, forceRefresh = forceRefresh, maxFiles = maxFiles)
}

@Serializable
internal data class IndexStatusArgs(
    @Description("Absolute path to a previously indexed Plugins folder.")
    val rootPath: String,
) {
    fun toRequest() = IndexStatusRequest(rootPath = rootPath)
}

@Serializable
internal data class SearchIndexArgs(
    @Description("Absolute path to an indexed Plugins folder. Never scans; run index_plugins first.")
    val rootPath: String,
    @Description("Free text matched case-insensitively against path, label, exemplar name, type, class, and TGI.")
    val query: String? = null,
    @Description("Keep only entries of this resource kind.")
    val kindFilter: KnownEntryKind? = null,
    @Description("Keep only entries with this object class, for example \"Lot\" or \"Prop\".")
    val objectClass: String? = null,
    @Description("Keep only exemplars carrying this property ID, as hex.")
    @Pattern(regex = HEX32_PATTERN)
    val propertyIdHex: String? = null,
    @Description("Maximum matches to return in this page.")
    @MinInt(1)
    @MaxInt(MAX_SEARCH_LIMIT.toLong())
    val limit: Int? = DEFAULT_SEARCH_LIMIT,
    @Description("Number of matches to skip before this page.")
    @MinInt(0)
    val offset: Int? = null,
) {
    init {
        limit.requireInRange("limit", 1, MAX_SEARCH_LIMIT)
        offset.requireInRange("offset", 0, Int.MAX_VALUE)
    }

    fun toRequest() = SearchIndexRequest(
        rootPath = rootPath,
        query = query,
        kindFilter = kindFilter,
        objectClass = objectClass,
        propertyId = propertyIdHex?.let { parseHex(it, "propertyIdHex") },
        limit = limit,
        offset = offset,
    )
}

// ---------------------------------------------------------------------------------------------
// Read one entry by TGI
// ---------------------------------------------------------------------------------------------

@Serializable
internal data class EntryByTgiArgs(
    @Description("Absolute path to the DBPF package containing the entry.")
    val path: String,
    @Description(TGI_TRIPLE_DESCRIPTION)
    override val tgi: String? = null,
    @Description("Type ID as hex. Use with group and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val type: String? = null,
    @Description("Group ID as hex. Use with type and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val group: String? = null,
    @Description("Instance ID as hex. Use with type and group instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val instance: String? = null,
) : TgiArguments

@Serializable
internal data class ExemplarByTgiArgs(
    @Description("Absolute path to the DBPF package containing the entry.")
    val path: String,
    @Description(TGI_TRIPLE_DESCRIPTION)
    override val tgi: String? = null,
    @Description("Type ID as hex. Use with group and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val type: String? = null,
    @Description("Group ID as hex. Use with type and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val group: String? = null,
    @Description("Instance ID as hex. Use with type and group instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val instance: String? = null,
    @Description("Also resolve the parent cohort chain.")
    val resolveParent: Boolean = false,
    @Description("Plugins folder to resolve cross-package parent cohorts against. Requires a current index.")
    val rootPath: String? = null,
) : TgiArguments

@Serializable
internal data class ExportEntryByTgiArgs(
    @Description("Absolute path to the DBPF package containing the entry.")
    val path: String,
    @Description("Absolute path of the file to write. An existing file is replaced.")
    val outputPath: String,
    @Description(TGI_TRIPLE_DESCRIPTION)
    override val tgi: String? = null,
    @Description("Type ID as hex. Use with group and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val type: String? = null,
    @Description("Group ID as hex. Use with type and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val group: String? = null,
    @Description("Instance ID as hex. Use with type and group instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val instance: String? = null,
) : TgiArguments

@Serializable
internal data class ReadFshArgs(
    @Description("Absolute path to the DBPF package containing the FSH entry.")
    val path: String,
    @Description(TGI_TRIPLE_DESCRIPTION)
    override val tgi: String? = null,
    @Description("Type ID as hex. Use with group and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val type: String? = null,
    @Description("Group ID as hex. Use with type and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val group: String? = null,
    @Description("Instance ID as hex. Use with type and group instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val instance: String? = null,
    @Description("Element to decode a PNG preview from. Omit for metadata only.")
    @MinInt(0)
    val previewElementIndex: Int? = null,
    @Description("Mip level within the element to preview. 0 is full size.")
    @MinInt(0)
    val previewImageIndex: Int? = null,
) : TgiArguments {
    init {
        previewElementIndex.requireInRange("previewElementIndex", 0, Int.MAX_VALUE)
        previewImageIndex.requireInRange("previewImageIndex", 0, Int.MAX_VALUE)
    }
}

@Serializable
internal data class ImageEntryArgs(
    @Description("Absolute path to the DBPF package containing the image entry.")
    val path: String,
    @Description(TGI_TRIPLE_DESCRIPTION)
    override val tgi: String? = null,
    @Description("Type ID as hex. Use with group and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val type: String? = null,
    @Description("Group ID as hex. Use with type and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val group: String? = null,
    @Description("Instance ID as hex. Use with type and group instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val instance: String? = null,
    @Description("FSH element index. Ignored for native PNG entries.")
    @MinInt(0)
    val elementIndex: Int? = null,
    @Description("Mip level within the element. 0 is full size.")
    @MinInt(0)
    val imageIndex: Int? = null,
) : TgiArguments {
    init {
        elementIndex.requireInRange("elementIndex", 0, Int.MAX_VALUE)
        imageIndex.requireInRange("imageIndex", 0, Int.MAX_VALUE)
    }
}

@Serializable
internal data class ExportFshPngArgs(
    @Description("Absolute path to the DBPF package containing the FSH entry.")
    val path: String,
    @Description("Absolute path of the PNG file to write. An existing file is replaced.")
    val outputPath: String,
    @Description(TGI_TRIPLE_DESCRIPTION)
    override val tgi: String? = null,
    @Description("Type ID as hex. Use with group and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val type: String? = null,
    @Description("Group ID as hex. Use with type and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val group: String? = null,
    @Description("Instance ID as hex. Use with type and group instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val instance: String? = null,
    @Description("FSH element index to export.")
    @MinInt(0)
    val elementIndex: Int? = null,
    @Description("Mip level within the element to export. 0 is full size.")
    @MinInt(0)
    val imageIndex: Int? = null,
) : TgiArguments {
    init {
        elementIndex.requireInRange("elementIndex", 0, Int.MAX_VALUE)
        imageIndex.requireInRange("imageIndex", 0, Int.MAX_VALUE)
    }
}

@Serializable
internal data class RawEntryArgs(
    @Description("Absolute path to the DBPF package containing the entry.")
    val path: String,
    @Description(TGI_TRIPLE_DESCRIPTION)
    override val tgi: String? = null,
    @Description("Type ID as hex. Use with group and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val type: String? = null,
    @Description("Group ID as hex. Use with type and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val group: String? = null,
    @Description("Instance ID as hex. Use with type and group instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val instance: String? = null,
    @Description("Maximum bytes to return. Larger entries are truncated.")
    @MinInt(1)
    @MaxInt(MAX_ENTRY_PREVIEW_BYTES.toLong())
    val maxBytes: Int? = DEFAULT_RAW_MAX_BYTES,
) : TgiArguments {
    init {
        maxBytes.requireInRange("maxBytes", 1, MAX_ENTRY_PREVIEW_BYTES)
    }
}

@Serializable
internal data class KeyCfgArgs(
    @Description("Absolute path to the DBPF package containing the KEYCFG entry.")
    val path: String,
    @Description(TGI_TRIPLE_DESCRIPTION)
    override val tgi: String? = null,
    @Description("Type ID as hex. Use with group and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val type: String? = null,
    @Description("Group ID as hex. Use with type and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val group: String? = null,
    @Description("Instance ID as hex. Use with type and group instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val instance: String? = null,
    @Description("Maximum decoded bytes to scan for text.")
    @MinInt(1)
    @MaxInt(MAX_ENTRY_PREVIEW_BYTES.toLong())
    val maxBytes: Int? = DEFAULT_KEYCFG_MAX_BYTES,
) : TgiArguments {
    init {
        maxBytes.requireInRange("maxBytes", 1, MAX_ENTRY_PREVIEW_BYTES)
    }
}

@Serializable
internal data class TabBinaryArgs(
    @Description("Absolute path to the DBPF package containing the TAB entry.")
    val path: String,
    @Description(TGI_TRIPLE_DESCRIPTION)
    override val tgi: String? = null,
    @Description("Type ID as hex. Use with group and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val type: String? = null,
    @Description("Group ID as hex. Use with type and instance instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val group: String? = null,
    @Description("Instance ID as hex. Use with type and group instead of tgi.")
    @Pattern(regex = HEX32_PATTERN)
    override val instance: String? = null,
    @Description("Maximum decoded bytes to inspect.")
    @MinInt(1)
    @MaxInt(MAX_ENTRY_PREVIEW_BYTES.toLong())
    val maxBytes: Int? = DEFAULT_TAB_MAX_BYTES,
    @Description("Maximum little-endian 32-bit words to report.")
    @MinInt(1)
    @MaxInt(MAX_TAB_WORDS.toLong())
    val maxWords: Int? = DEFAULT_TAB_MAX_WORDS,
) : TgiArguments {
    init {
        maxBytes.requireInRange("maxBytes", 1, MAX_ENTRY_PREVIEW_BYTES)
        maxWords.requireInRange("maxWords", 1, MAX_TAB_WORDS)
    }
}

// ---------------------------------------------------------------------------------------------
// Property registry and QFS
// ---------------------------------------------------------------------------------------------

@Serializable
internal data class DescribePropertyArgs(
    @Description("Property ID as hex, for example \"00000010\" for Exemplar Type.")
    @Pattern(regex = HEX32_PATTERN)
    val id: String,
) {
    fun toRequest() = DescribePropertyRequest(id = parseHex(id, "id"))
}

@Serializable
internal data class DecodePropertyValueArgs(
    @Description("Property ID as hex.")
    @Pattern(regex = HEX32_PATTERN)
    val id: String,
    @Description(
        "Raw values to interpret. Numbers, booleans, and strings are all accepted; a resource-key " +
            "property expects a flat run of (type, group, instance) triplets.",
    )
    val values: List<JsonElement>,
) {
    fun toRequest() = DecodePropertyValueRequest(id = parseHex(id, "id"), values = values)
}

@Serializable
internal data class DecodeQfsArgs(
    @Description("Base64-encoded QFS/RefPack stream to decode.")
    val payloadBase64: String,
    @Description("Maximum decoded bytes to return.")
    @MinInt(1)
    @MaxInt(MAX_ENTRY_PREVIEW_BYTES.toLong())
    val maxBytes: Int? = DEFAULT_RAW_MAX_BYTES,
    @Description("True when the payload begins with the 4-byte DBPF compressed-size prefix.")
    val hasDbpfSizePrefix: Boolean? = null,
) {
    init {
        maxBytes.requireInRange("maxBytes", 1, MAX_ENTRY_PREVIEW_BYTES)
    }

    fun toRequest() = DecodeQfsRequest(
        payloadBase64 = payloadBase64,
        maxBytes = maxBytes,
        hasDbpfSizePrefix = hasDbpfSizePrefix,
    )
}

// ---------------------------------------------------------------------------------------------
// Network INI
// ---------------------------------------------------------------------------------------------

@Serializable
internal data class ReadIniArgs(
    @Description("Absolute path to the DBPF package containing the Network INI entry.")
    val path: String,
    @Description("TGI of the INI entry, for example \"00000000-8A5971C5-8A5993B9\".")
    val tgi: String,
) {
    fun toRequest() = ReadIniRequest(path = path, tgi = parseTgi(tgi))
}

@Serializable
internal data class WriteIniArgs(
    @Description("Absolute path of the DBPF package to create or patch.")
    val outputPath: String,
    @Description("TGI to install the INI text at, for example \"00000000-8A5971C5-8A5993B9\".")
    val tgi: String,
    @Description("Exact INI text to store. It is not parsed, normalized, reordered, or deduplicated.")
    val text: String,
    @Description("QFS-compress the new entry.")
    val compressed: Boolean = true,
    @Description("Replace an existing output file entirely.")
    val overwrite: Boolean = false,
    @Description("Keep entries already in the output file and replace only the matching TGI.")
    val merge: Boolean = false,
) {
    fun toRequest() = WriteIniRequest(
        outputPath = outputPath,
        tgi = parseTgi(tgi),
        text = text,
        compressed = compressed,
        overwrite = overwrite,
        merge = merge,
    )
}

// ---------------------------------------------------------------------------------------------
// Write tools
// ---------------------------------------------------------------------------------------------

@Serializable
internal data class ExemplarPropertyArgs(
    @Description("Property ID as hex.")
    @Pattern(regex = HEX32_PATTERN)
    val id: String,
    @Description(
        "Value type. Omit to infer it from the bundled SC4 property registry. An explicit type " +
            "always wins, which is how custom and modded properties are written.",
    )
    val type: ExemplarPropertyType? = null,
    @Description("Values to store. Use a flat run of (type, group, instance) numbers for a Tgi property.")
    val values: List<JsonElement>,
) {
    fun toInput() = ExemplarPropertyInput(id = parseHex(id, "id"), type = type?.name, values = values)
}

/** The property types the exemplar writer can encode. */
@Serializable
@Suppress("EnumEntryName")
internal enum class ExemplarPropertyType {
    Uint8,
    Uint16,
    Uint32,
    Sint32,
    Sint64,
    Float32,
    Bool,
    String,
    Tgi,
}

@Serializable
internal data class ExemplarEntryArgs(
    @Description("TGI of the entry to write.")
    val tgi: String,
    @Description("Write a cohort instead of an exemplar.")
    val isCohort: Boolean = false,
    @Description("Parent cohort TGI, or omit for none.")
    val parentCohortTgi: String? = null,
    @Description("Properties to store on the entry.")
    val properties: List<ExemplarPropertyArgs>,
) {
    fun toEntry() = ExemplarWriteEntry(
        tgi = parseTgi(tgi),
        isCohort = isCohort,
        parentCohort = parentCohortTgi?.let(::parseTgi),
        properties = properties.map { it.toInput() },
    )
}

@Serializable
internal data class WriteExemplarsArgs(
    @Description("Absolute path of the DBPF package to create or patch.")
    val outputPath: String,
    @Description("Exemplar and cohort entries to write. TGIs must be unique within one request.")
    val entries: List<ExemplarEntryArgs>,
    @Description("Check declared property types against the bundled registry and report mismatches as warnings.")
    val validateAgainstRegistry: Boolean = true,
    @Description("QFS-compress the new entries.")
    val compressed: Boolean = true,
    @Description("Replace an existing output file entirely.")
    val overwrite: Boolean = false,
    @Description("Keep entries already in the output file that this request does not address.")
    val merge: Boolean = false,
) {
    fun toRequest() = WriteExemplarsRequest(
        outputPath = outputPath,
        entries = entries.map { it.toEntry() },
        validateAgainstRegistry = validateAgainstRegistry,
        compressed = compressed,
        overwrite = overwrite,
        merge = merge,
    )
}

@Serializable
internal data class LTextEntryArgs(
    @Description("TGI of the LTEXT entry to write.")
    val tgi: String,
    @Description("Text to store. Encoded as UTF-16.")
    val text: String,
) {
    fun toEntry() = LTextWriteEntry(tgi = parseTgi(tgi), text = text)
}

@Serializable
internal data class WriteLTextArgs(
    @Description("Absolute path of the DBPF package to create or patch.")
    val outputPath: String,
    @Description("LTEXT entries to write. TGIs must be unique within one request.")
    val entries: List<LTextEntryArgs>,
    @Description("QFS-compress the new entries.")
    val compressed: Boolean = true,
    @Description("Replace an existing output file entirely.")
    val overwrite: Boolean = false,
    @Description("Keep entries already in the output file that this request does not address.")
    val merge: Boolean = false,
) {
    fun toRequest() = WriteLTextRequest(
        outputPath = outputPath,
        entries = entries.map { it.toEntry() },
        compressed = compressed,
        overwrite = overwrite,
        merge = merge,
    )
}

/** FSH surface formats this build can encode. Dxt5 decodes but cannot be encoded. */
@Serializable
@Suppress("EnumEntryName")
internal enum class FshFormatArg {
    Dxt1,
    Dxt3,
    A8R8G8B8,
    A0R8G8B8,
    A1R5G5B5,
    A0R5G6B5,
    A4R4G4B4,
}

@Serializable
internal data class FshElementArgs(
    @Description(
        "Surface format. Dxt1 and Dxt3 require image width and height to be multiples of 4. " +
            "Dxt5 encoding is not supported by the bundled scdbpf version.",
    )
    val format: FshFormatArg,
    @Description("Optional four-character element label.")
    val label: String? = null,
    @Description(
        "Mip chain as base64 PNGs, largest first. Mip levels are not generated automatically: " +
            "each subsequent image must be exactly half the width and height of the previous one.",
    )
    val imagesPngBase64: List<String>,
) {
    fun toInput() = FshElementInput(
        format = format.name,
        label = label,
        imagesPngBase64 = imagesPngBase64,
    )
}

@Serializable
internal data class FshEntryArgs(
    @Description("TGI of the FSH entry to write.")
    val tgi: String,
    @Description("FSH directory ID, for example \"G264\".")
    val dirId: String? = null,
    @Description("Elements to encode into this entry.")
    val elements: List<FshElementArgs>,
) {
    fun toEntry() = FshWriteEntry(
        tgi = parseTgi(tgi),
        dirId = dirId ?: "G264",
        elements = elements.map { it.toInput() },
    )
}

@Serializable
internal data class WriteFshArgs(
    @Description("Absolute path of the DBPF package to create or patch.")
    val outputPath: String,
    @Description("FSH entries to write. TGIs must be unique within one request.")
    val entries: List<FshEntryArgs>,
    @Description("QFS-compress the new entries.")
    val compressed: Boolean = true,
    @Description("Replace an existing output file entirely.")
    val overwrite: Boolean = false,
    @Description("Keep entries already in the output file that this request does not address.")
    val merge: Boolean = false,
) {
    fun toRequest() = WriteFshRequest(
        outputPath = outputPath,
        entries = entries.map { it.toEntry() },
        compressed = compressed,
        overwrite = overwrite,
        merge = merge,
    )
}

@Serializable
internal data class RawEntryWriteArgs(
    @Description("TGI of the entry to write.")
    val tgi: String,
    @Description("Entry payload as base64. Stored verbatim with no format encoding.")
    val payloadBase64: String,
) {
    fun toEntry() = RawWriteEntry(tgi = parseTgi(tgi), payloadBase64 = payloadBase64)
}

@Serializable
internal data class WriteRawEntriesArgs(
    @Description("Absolute path of the DBPF package to create or patch.")
    val outputPath: String,
    @Description("Entries to write verbatim. TGIs must be unique within one request.")
    val entries: List<RawEntryWriteArgs>,
    @Description("QFS-compress the new entries.")
    val compressed: Boolean = true,
    @Description("Replace an existing output file entirely.")
    val overwrite: Boolean = false,
    @Description("Keep entries already in the output file that this request does not address.")
    val merge: Boolean = false,
) {
    fun toRequest() = WriteRawEntriesRequest(
        outputPath = outputPath,
        entries = entries.map { it.toEntry() },
        compressed = compressed,
        overwrite = overwrite,
        merge = merge,
    )
}
