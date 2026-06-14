package com.github.caspervg.dbpfmcp.backend.scdbpf

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QfsDecoderTest {
    @Test
    fun `decodes manual packet classes`() {
        val stream = qfsHeader(86).apply {
            add(0xEF.toByte())
            addAll(ByteArray(64) { 'A'.code.toByte() }.asIterable())
            add(0x1F.toByte())
            add(0x42.toByte())
            addAll("BCD".encodeToByteArray().asIterable())
            add(0x80.toByte())
            add(0x00.toByte())
            add(0x0C.toByte())
            add(0xC0.toByte())
            add(0x00.toByte())
            add(0x50.toByte())
            add(0x00.toByte())
            add(0xFC.toByte())
        }.toByteArray()

        val expected = ByteArray(64) { 'A'.code.toByte() } +
            "BCD".encodeToByteArray() +
            ByteArray(10) { 'A'.code.toByte() } +
            "BCDA".encodeToByteArray() +
            ByteArray(5) { 'A'.code.toByte() }

        assertContentEquals(expected, QfsDecoder.decode(stream)?.bytes)
    }

    @Test
    fun `decodes extended header`() {
        val stream = qfsHeader(3, extended = true).apply {
            add(0xFF.toByte())
            addAll("SC4".encodeToByteArray().asIterable())
        }.toByteArray()

        val result = QfsDecoder.decode(stream)

        assertContentEquals("SC4".encodeToByteArray(), result?.bytes)
        assertTrue(result?.extendedHeader == true)
    }

    @Test
    fun `rejects malformed streams`() {
        assertFalse(QfsDecoder.isQfsCompressed(byteArrayOf(0x10, 0xFA.toByte(), 0, 0, 0, 0xFC.toByte())))
        assertNull(QfsDecoder.decode(byteArrayOf(0x10, 0xFB.toByte(), 0, 0, 4, 0xFC.toByte())))
    }

    @Test
    fun `detects qfs stream after dbpf size prefix`() {
        val stream = byteArrayOf(6, 0, 0, 0) + qfsHeader(0).apply { add(0xFC.toByte()) }.toByteArray()

        assertTrue(QfsDecoder.isQfsCompressed(stream, offset = 4))
        assertEquals(0, QfsDecoder.decode(stream, offset = 4)?.bytes?.size)
    }

    private fun qfsHeader(size: Int, extended: Boolean = false): MutableList<Byte> {
        val header = mutableListOf(
            (if (extended) 0x11 else 0x10).toByte(),
            0xFB.toByte(),
            ((size shr 16) and 0xFF).toByte(),
            ((size shr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte(),
        )
        if (extended) {
            header.addAll(listOf(0, 0, 0))
        }
        return header
    }
}
