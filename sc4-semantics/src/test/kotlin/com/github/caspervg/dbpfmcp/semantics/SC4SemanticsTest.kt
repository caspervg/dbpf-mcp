package com.github.caspervg.dbpfmcp.semantics

import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SC4SemanticsTest {
    @Test
    fun `parse accepts prefixed hex`() {
        val tgi = parseTgi("0x6534284A-0x00000000-0x12345678")

        assertEquals(SC4TypeIds.EXEMPLAR, tgi.type)
        assertEquals(0L, tgi.group)
        assertEquals(0x12345678L, tgi.instance)
    }

    @Test
    fun `format is fixed width`() {
        assertEquals("6534284A", formatHex32(SC4TypeIds.EXEMPLAR))
        assertEquals("000000001234ABCD", formatHex64(0x1234ABCDL))
    }

    @Test
    fun `invalid tgi is rejected`() {
        assertFailsWith<Exception> {
            parseTgi("bad-value")
        }
    }

    @Test
    fun `property value decoding normalizes integer types`() {
        val decoded = decodePropertyValue(0xEBFC5E26L, listOf(JsonPrimitive("0x00000003")))

        assertNotNull(decoded)
        assertEquals("Uint32", decoded.property.type)
        assertEquals(3L, decoded.values.first().decimal)
        assertEquals("00000003", decoded.values.first().hex)
    }

    @Test
    fun `exemplar type decoding exposes semantic label`() {
        val decoded = decodePropertyValue(0x00000010L, listOf(JsonPrimitive("0x0000000B")))

        assertNotNull(decoded)
        assertEquals("exemplarType", decoded.semanticType)
        assertEquals("Network", decoded.values.first().label)
    }

    @Test
    fun `resource key decoding groups triple into TGI interpretation`() {
        val decoded = decodePropertyValue(
            0x27812820L,
            listOf(
                JsonPrimitive("0x5AD0E817"),
                JsonPrimitive("0xBADB57F1"),
                JsonPrimitive("0x0C006800"),
            ),
        )

        assertNotNull(decoded)
        assertEquals("resourceKeyList", decoded.semanticType)
        val interpretation = decoded.interpretation.toString()
        assertEquals(true, interpretation.contains("5AD0E817"))
        assertEquals(true, interpretation.contains("S3D"))
    }

    @Test
    fun `png type is recognized`() {
        assertEquals(KnownEntryKind.PNG, kindForType(SC4TypeIds.PNG))
    }
}
