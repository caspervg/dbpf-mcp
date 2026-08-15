package com.github.caspervg.dbpfmcp.server

import com.github.caspervg.dbpfmcp.core.DecodedPropertyValue
import com.github.caspervg.dbpfmcp.core.EntrySummary
import com.github.caspervg.dbpfmcp.core.ExemplarModel
import com.github.caspervg.dbpfmcp.core.ExemplarProperty
import com.github.caspervg.dbpfmcp.core.ExplainEntryResult
import com.github.caspervg.dbpfmcp.core.ExplanationField
import com.github.caspervg.dbpfmcp.core.ExplanationRelationship
import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import com.github.caspervg.dbpfmcp.core.ListEntriesResult
import com.github.caspervg.dbpfmcp.core.ParentChainItem
import com.github.caspervg.dbpfmcp.core.PropertyDescription
import com.github.caspervg.dbpfmcp.core.Tgi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pins the wire format across the move from hand-written `buildJsonObject` encoders to
 * kotlinx.serialization.
 *
 * The rule this enforces, for every model checked: the generated encoding carries the same field
 * names and the same values as the encoder it replaced, except that a field the old encoder wrote
 * as an explicit `null` is now simply absent. That one difference is deliberate — these payloads
 * are read by a model, where a screenful of nulls is pure token cost.
 *
 * The check that matters most is that TGI components and property IDs stay *hexadecimal strings*.
 * They are `Long` in the models, so a naive `encodeToJsonElement` would emit `1697577546` where
 * every SC4 tool writes `6534284A`.
 */
class WireFormatCompatibilityTest {

    private val exemplarTgi = Tgi(type = 0x6534284AL, group = 0xA8434037L, instance = 0x0C006800L)

    @Test
    fun `tgi components stay hexadecimal strings`() {
        val encoded = ServerJson.encodeResult(exemplarTgi)

        assertEquals(JsonPrimitive("6534284A"), encoded["type"])
        assertEquals(JsonPrimitive("A8434037"), encoded["group"])
        assertEquals(JsonPrimitive("0C006800"), encoded["instance"])
        assertMatchesLegacy(LegacyEncoders.tgiJson(exemplarTgi), encoded)
    }

    @Test
    fun `negative signed values do not overflow the 32 bit hex field`() {
        val encoded = ServerJson.encodeResult(Tgi(type = -1L, group = 0L, instance = 1L))

        assertEquals(JsonPrimitive("FFFFFFFF"), encoded["type"])
        assertEquals(JsonPrimitive("00000000"), encoded["group"])
    }

    @Test
    fun `exemplar property matches the previous encoder`() {
        val property = ExemplarProperty(
            id = 0x27812820L,
            name = "Resource Key Type 1",
            valueType = "Uint32",
            expectedType = "Uint32",
            typeMatchesExpected = true,
            values = listOf(JsonPrimitive(1), JsonPrimitive(2), JsonPrimitive(3)),
            decodedValues = listOf(
                DecodedPropertyValue(
                    index = 0,
                    raw = JsonPrimitive(1),
                    normalized = JsonPrimitive(1),
                    decimal = 1,
                    hex = "00000001",
                ),
            ),
            semanticType = "resourceKeyList",
        )

        assertMatchesLegacy(LegacyEncoders.propertyJson(property), ServerJson.encodeResult(property))
    }

    @Test
    fun `property with only required fields drops its nulls`() {
        val property = ExemplarProperty(
            id = 0x10L,
            valueType = "Uint32",
            values = listOf(JsonPrimitive(2)),
        )
        val encoded = ServerJson.encodeResult(property)

        assertEquals(JsonPrimitive("00000010"), encoded["id"])
        assertTrue("name" !in encoded, "expected absent null field, got ${encoded["name"]}")
        assertTrue("expectedType" !in encoded)
        assertMatchesLegacy(LegacyEncoders.propertyJson(property), encoded)
    }

    @Test
    fun `exemplar model matches the previous encoder`() {
        val model = ExemplarModel(
            tgi = exemplarTgi,
            parentCohort = Tgi(0x05342861L, 0xB03697D1L, 0x00000000L),
            exemplarName = "Test Building",
            properties = listOf(
                ExemplarProperty(id = 0x10L, valueType = "Uint32", values = listOf(JsonPrimitive(2))),
            ),
            parentChain = listOf(
                ParentChainItem(
                    tgi = Tgi(0x05342861L, 0xB03697D1L, 0x00000000L),
                    name = "Parent Cohort",
                    propertyCount = 4,
                    resolved = true,
                ),
            ),
        )

        val encoded = ServerJson.encodeResult(model)

        assertMatchesLegacy(LegacyEncoders.exemplarJson(model), encoded, allowNewKeys = true)
        // Added alongside the fix that stops one registry type mismatch aborting the whole read.
        assertEquals(JsonArray(emptyList()), encoded["warnings"])
    }

    @Test
    fun `list entries result keeps its previous fields and adds paging detail`() {
        val result = ListEntriesResult(
            packagePath = "/plugins/example.dat",
            entryCount = 12,
            matchCount = 3,
            offset = 0,
            limit = 2,
            truncated = true,
            entries = listOf(
                EntrySummary(tgi = exemplarTgi, kind = KnownEntryKind.EXEMPLAR, size = 512, compressed = true),
            ),
        )
        val encoded = ServerJson.encodeResult(result)

        assertMatchesLegacy(LegacyEncoders.listEntriesJson(result), encoded, allowNewKeys = true)
        // The fields that make truncation visible; previously a caller could not tell.
        assertEquals(JsonPrimitive(3), encoded["matchCount"])
        assertEquals(JsonPrimitive(true), encoded["truncated"])
    }

    @Test
    fun `property description keeps its hexadecimal id`() {
        val description = PropertyDescription(
            id = 0x00000010L,
            name = "Exemplar Type",
            type = "Uint32",
            description = "Used by property editors",
            group = "General Properties",
        )
        val encoded = ServerJson.encodeResult(description)

        assertEquals(JsonPrimitive("00000010"), encoded["id"])
        assertMatchesLegacy(LegacyEncoders.propertyDescriptionJson(description), encoded)
    }

    @Test
    fun `explain entry result matches the previous encoder`() {
        val result = ExplainEntryResult(
            packagePath = "/plugins/example.dat",
            tgi = exemplarTgi,
            kind = KnownEntryKind.EXEMPLAR,
            summary = "An exemplar describing a building.",
            importantFields = listOf(ExplanationField("name", "Test Building")),
            relationships = listOf(
                ExplanationRelationship(kind = "parentCohort", tgi = exemplarTgi, resolved = true),
            ),
            warnings = listOf("example warning"),
            suggestedNextTools = listOf("read_exemplar"),
        )

        assertMatchesLegacy(LegacyEncoders.explanationJson(result), ServerJson.encodeResult(result))
    }

    /**
     * Asserts [actual] carries every field of [legacy] with the same value, at any depth, with two
     * deliberate exceptions:
     *
     *  - a field [legacy] wrote as an explicit `null` is now absent;
     *  - a nullable list [legacy] wrote as `[]` is now absent when it is null. The old encoder
     *    built the array unconditionally and then iterated a possibly-null list, so it could not
     *    tell "no value" from "empty"; an absent field and an empty array both mean nothing to
     *    report here.
     *
     * Anything else — a renamed field, a reordered TGI, a hex identifier turned into a number — is
     * a failure.
     */
    private fun assertMatchesLegacy(legacy: JsonObject, actual: JsonObject, allowNewKeys: Boolean = false) {
        legacy.forEach { (key, legacyValue) ->
            val actualValue = actual[key]
            when {
                legacyValue is JsonNull ->
                    assertTrue(key !in actual, "field '$key' was null before and should now be absent")

                legacyValue is JsonArray && legacyValue.isEmpty() && actualValue == null -> Unit

                actualValue == null -> fail("field '$key' is missing from the generated encoding")

                legacyValue is JsonObject && actualValue is JsonObject ->
                    assertMatchesLegacy(legacyValue, actualValue, allowNewKeys)

                legacyValue is JsonArray && actualValue is JsonArray ->
                    assertArrayMatchesLegacy(key, legacyValue, actualValue, allowNewKeys)

                else -> assertEquals(legacyValue, actualValue, "field '$key' changed value")
            }
        }
        if (!allowNewKeys) {
            val added = actual.keys - legacy.keys
            assertTrue(added.isEmpty(), "generated encoding introduced unexpected fields: $added")
        }
    }

    private fun assertArrayMatchesLegacy(
        key: String,
        legacy: JsonArray,
        actual: JsonArray,
        allowNewKeys: Boolean,
    ) {
        assertEquals(legacy.size, actual.size, "array '$key' changed length")
        legacy.forEachIndexed { index, legacyElement ->
            val actualElement = actual[index]
            if (legacyElement is JsonObject && actualElement is JsonObject) {
                assertMatchesLegacy(legacyElement, actualElement, allowNewKeys)
            } else {
                assertEquals(legacyElement, actualElement, "array '$key' changed at index $index")
            }
        }
    }
}
