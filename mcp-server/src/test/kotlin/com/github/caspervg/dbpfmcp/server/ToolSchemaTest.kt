package com.github.caspervg.dbpfmcp.server

import com.github.caspervg.dbpfmcp.core.ExemplarModel
import com.github.caspervg.dbpfmcp.core.ListEntriesResult
import com.github.caspervg.dbpfmcp.core.SearchIndexResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Checks that every tool's schema can actually be generated, and that the constraints the old
 * hand-written schemas lacked are now present.
 *
 * Generation is not guaranteed to succeed for every type: `JsonElement`-valued fields are
 * contextual, and a type that resolves to something other than an object schema would be rejected
 * by [ToolSchemas]. Since generation happens at server start-up, a type the generator cannot
 * handle would take the whole server down rather than fail one tool, so it is worth asserting.
 */
class ToolSchemaTest {

    @Test
    fun `every tool argument type produces an input schema`() {
        val schemas = mapOf<String, Tool.Input>(
            "list_entries" to ToolSchemas.input<ListEntriesArgs>(),
            "summarize_package" to ToolSchemas.input<PackagePathArgs>(),
            "inspect_package" to ToolSchemas.input<InspectPackageArgs>(),
            "index_plugins" to ToolSchemas.input<IndexPluginsArgs>(),
            "index_status" to ToolSchemas.input<IndexStatusArgs>(),
            "search_index" to ToolSchemas.input<SearchIndexArgs>(),
            "explain_entry" to ToolSchemas.input<EntryByTgiArgs>(),
            "read_exemplar" to ToolSchemas.input<ExemplarByTgiArgs>(),
            "export_exemplar_text" to ToolSchemas.input<ExportEntryByTgiArgs>(),
            "read_fsh" to ToolSchemas.input<ReadFshArgs>(),
            "read_image_entry" to ToolSchemas.input<ImageEntryArgs>(),
            "export_fsh_png" to ToolSchemas.input<ExportFshPngArgs>(),
            "read_raw_entry" to ToolSchemas.input<RawEntryArgs>(),
            "read_keycfg" to ToolSchemas.input<KeyCfgArgs>(),
            "read_tab_binary" to ToolSchemas.input<TabBinaryArgs>(),
            "describe_property" to ToolSchemas.input<DescribePropertyArgs>(),
            "decode_property_value" to ToolSchemas.input<DecodePropertyValueArgs>(),
            "decode_qfs" to ToolSchemas.input<DecodeQfsArgs>(),
            "read_ini" to ToolSchemas.input<ReadIniArgs>(),
            "write_ini" to ToolSchemas.input<WriteIniArgs>(),
            "write_exemplars" to ToolSchemas.input<WriteExemplarsArgs>(),
            "write_ltext" to ToolSchemas.input<WriteLTextArgs>(),
            "write_fsh" to ToolSchemas.input<WriteFshArgs>(),
            "write_raw_entries" to ToolSchemas.input<WriteRawEntriesArgs>(),
        )

        schemas.forEach { (tool, schema) ->
            assertTrue(schema.properties.isNotEmpty(), "$tool produced an empty input schema")
        }
    }

    @Test
    fun `result types produce output schemas`() {
        assertTrue(ToolSchemas.output<ListEntriesResult>().properties.isNotEmpty())
        assertTrue(ToolSchemas.output<ExemplarModel>().properties.isNotEmpty())
        assertTrue(ToolSchemas.output<SearchIndexResult>().properties.isNotEmpty())
    }

    @Test
    fun `required arguments are marked required and optional ones are not`() {
        val schema = ToolSchemas.input<ListEntriesArgs>()

        assertTrue("path" in schema.required.orEmpty(), "path is mandatory but was not marked required")
        assertTrue("limit" !in schema.required.orEmpty(), "limit has a default and must not be required")
    }

    @Test
    fun `integer bounds reach the schema instead of living only in prose`() {
        val limit = ToolSchemas.input<ListEntriesArgs>().property("limit")

        assertEquals(0, limit["minimum"]?.jsonPrimitive?.content?.toDouble()?.toInt())
        assertEquals(MAX_LIST_LIMIT, limit["maximum"]?.jsonPrimitive?.content?.toDouble()?.toInt())
    }

    @Test
    fun `kind filter is a real enum rather than a sentence listing the values`() {
        val kindFilter = ToolSchemas.input<ListEntriesArgs>().property("kindFilter")
        val values = kindFilter["enum"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: fail("kindFilter should declare an enum, got $kindFilter")

        assertTrue("EXEMPLAR" in values, "expected EXEMPLAR among $values")
        assertTrue("SC4PATHS" in values, "expected SC4PATHS among $values")
    }

    @Test
    fun `exemplar property type is an enum of the writable types`() {
        val schema = ToolSchemas.input<WriteExemplarsArgs>()

        // Nested inside entries[].properties[]; assert the enum survives inlining rather than
        // being emitted as a $ref the client cannot resolve.
        assertTrue(
            "Uint16" in schema.properties.toString(),
            "expected the writable property types to be inlined into the schema",
        )
        assertTrue("\$ref" !in schema.properties.toString(), "schemas must be inlined, not referenced")
    }

    @Test
    fun `previously undocumented parameters now carry descriptions`() {
        val listEntries = ToolSchemas.input<ListEntriesArgs>()
        listOf("typeFilterHex", "groupFilterHex", "labelContains").forEach { name ->
            assertTrue(
                listEntries.property(name).containsKey("description"),
                "'$name' still has no description",
            )
        }

        // The bare `tgi` fields on these tools had no description at all before.
        listOf(
            ToolSchemas.input<RawEntryArgs>(),
            ToolSchemas.input<KeyCfgArgs>(),
            ToolSchemas.input<TabBinaryArgs>(),
            ToolSchemas.input<ReadFshArgs>(),
            ToolSchemas.input<ImageEntryArgs>(),
            ToolSchemas.input<ExportFshPngArgs>(),
        ).forEach { schema ->
            assertTrue(schema.property("tgi").containsKey("description"), "tgi still has no description")
        }
    }

    @Test
    fun `hex arguments declare a pattern`() {
        val type = ToolSchemas.input<EntryByTgiArgs>().property("type")

        assertTrue(type.containsKey("pattern"), "hex arguments should constrain their format, got $type")
    }

    private fun Tool.Input.property(name: String): JsonObject =
        properties[name]?.jsonObject ?: fail("schema has no property '$name'; has ${properties.keys}")
}
