package com.github.caspervg.dbpfmcp.server

import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import com.github.caspervg.dbpfmcp.core.Tgi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the argument decoding path, which had no tests at all while it was ~20 hand-written
 * parsers picking fields out of a `JsonObject` one at a time.
 */
class ArgumentDecodingTest {

    private val expectedTgi = Tgi(type = 0x6534284AL, group = 0xA8434037L, instance = 0x0C006800L)

    @Test
    fun `both spellings of a TGI argument decode to the same value`() {
        val asString = ServerJson.decodeArguments<EntryByTgiArgs>(
            buildJsonObject {
                put("path", "/plugins/example.dat")
                put("tgi", "6534284A-A8434037-0C006800")
            },
        )
        val asTriple = ServerJson.decodeArguments<EntryByTgiArgs>(
            buildJsonObject {
                put("path", "/plugins/example.dat")
                put("type", "6534284A")
                put("group", "A8434037")
                put("instance", "0C006800")
            },
        )

        assertEquals(expectedTgi, asString.resolveTgi())
        assertEquals(expectedTgi, asTriple.resolveTgi())
    }

    @Test
    fun `colon and slash separators are accepted`() {
        listOf("6534284A:A8434037:0C006800", "6534284A/A8434037/0C006800").forEach { text ->
            val args = ServerJson.decodeArguments<EntryByTgiArgs>(
                buildJsonObject {
                    put("path", "/plugins/example.dat")
                    put("tgi", text)
                },
            )
            assertEquals(expectedTgi, args.resolveTgi(), "failed for '$text'")
        }
    }

    @Test
    fun `an incomplete TGI triple names the missing parts`() {
        val args = ServerJson.decodeArguments<EntryByTgiArgs>(
            buildJsonObject {
                put("path", "/plugins/example.dat")
                put("type", "6534284A")
                put("group", "A8434037")
            },
        )

        val failure = assertFailsWith<InputError> { args.resolveTgi() }
        assertTrue(
            failure.message.orEmpty().contains("instance"),
            "the error should say which part is missing, got: ${failure.message}",
        )
    }

    @Test
    fun `a missing required argument is reported as an input error`() {
        val failure = assertFailsWith<InputError> {
            ServerJson.decodeArguments<EntryByTgiArgs>(buildJsonObject { put("tgi", "1-2-3") })
        }

        assertTrue(
            failure.message.orEmpty().contains("path"),
            "the error should name the missing field, got: ${failure.message}",
        )
    }

    @Test
    fun `a wrongly typed argument is an input error rather than a generic failure`() {
        val failure = assertFailsWith<InputError> {
            ServerJson.decodeArguments<ListEntriesArgs>(
                buildJsonObject {
                    put("path", "/plugins/example.dat")
                    put("limit", "not a number")
                },
            )
        }

        assertTrue(failure.message.orEmpty().startsWith("Invalid tool arguments"), failure.message.orEmpty())
    }

    @Test
    fun `an out of range bound is rejected with the bound in the message`() {
        val failure = assertFailsWith<InputError> {
            ServerJson.decodeArguments<ListEntriesArgs>(
                buildJsonObject {
                    put("path", "/plugins/example.dat")
                    put("limit", MAX_LIST_LIMIT + 1)
                },
            )
        }

        assertTrue(failure.message.orEmpty().contains("limit"), failure.message.orEmpty())
    }

    @Test
    fun `unknown arguments are ignored rather than rejected`() {
        val args = ServerJson.decodeArguments<PackagePathArgs>(
            buildJsonObject {
                put("path", "/plugins/example.dat")
                put("somethingTheClientInvented", true)
            },
        )

        assertEquals("/plugins/example.dat", args.path)
    }

    @Test
    fun `defaults are applied when a field is omitted`() {
        val args = ServerJson.decodeArguments<ListEntriesArgs>(
            buildJsonObject { put("path", "/plugins/example.dat") },
        )

        assertEquals(DEFAULT_LIST_LIMIT, args.limit)
        assertNull(args.offset)
    }

    @Test
    fun `enum arguments decode by name`() {
        val args = ServerJson.decodeArguments<ListEntriesArgs>(
            buildJsonObject {
                put("path", "/plugins/example.dat")
                put("kindFilter", "EXEMPLAR")
            },
        )

        assertEquals(KnownEntryKind.EXEMPLAR, args.kindFilter)
    }

    @Test
    fun `hex filters are parsed with and without an 0x prefix`() {
        fun filter(value: String): Long? = ServerJson.decodeArguments<ListEntriesArgs>(
            buildJsonObject {
                put("path", "/plugins/example.dat")
                put("typeFilterHex", value)
            },
        ).toRequest().typeFilter

        assertEquals(0x6534284AL, filter("6534284A"))
        assertEquals(0x6534284AL, filter("0x6534284A"))
    }

    @Test
    fun `write requests map nested entries onto the domain types`() {
        val args = ServerJson.decodeArguments<WriteExemplarsArgs>(
            arguments = buildJsonObject {
                put("outputPath", "/plugins/out.dat")
                put(
                    "entries",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("tgi", "6534284A-A8434037-0C006800")
                                put("parentCohortTgi", "05342861-B03697D1-00000000")
                                put(
                                    "properties",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", "00000010")
                                                put("type", "Uint32")
                                                put(
                                                    "values",
                                                    buildJsonArray { add(JsonPrimitive(2)) },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

        val request = args.toRequest()
        val entry = request.entries.single()
        assertEquals(expectedTgi, entry.tgi)
        assertEquals(Tgi(0x05342861L, 0xB03697D1L, 0L), entry.parentCohort)
        assertEquals(0x10L, entry.properties.single().id)
        assertEquals("Uint32", entry.properties.single().type)
        // Defaults that the hand-written parser applied are still applied.
        assertTrue(request.compressed)
        assertTrue(request.validateAgainstRegistry)
    }
}
