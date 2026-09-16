package com.github.caspervg.dbpfmcp.integration

import com.github.caspervg.dbpfmcp.backend.scdbpf.ScdbpfAdapter
import com.github.caspervg.dbpfmcp.core.DecodeError
import com.github.caspervg.dbpfmcp.core.ExplainEntryRequest
import com.github.caspervg.dbpfmcp.core.ExportLuaTextRequest
import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.LTextWriteEntry
import com.github.caspervg.dbpfmcp.core.ListEntriesRequest
import com.github.caspervg.dbpfmcp.core.RawWriteEntry
import com.github.caspervg.dbpfmcp.core.ReadLTextRequest
import com.github.caspervg.dbpfmcp.core.ReadLuaRequest
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.core.WriteLTextRequest
import com.github.caspervg.dbpfmcp.core.WriteLuaEntryRequest
import com.github.caspervg.dbpfmcp.core.WriteRawEntriesRequest
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LuaIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    private val adapter = ScdbpfAdapter()
    private val luaTgi = Tgi(0xCA63E2A3L, 0x4A5E8EF6L, 1L)
    private val ltextTgi = Tgi(0x2026960BL, 0L, 2L)

    @Test
    fun `Lua writer requires explicit merge and preserves other entries`() {
        val output = tempDir.resolve("output.dat").toString()
        adapter.writeLText(
            WriteLTextRequest(outputPath = output, entries = listOf(LTextWriteEntry(ltextTgi, "Original"))),
        )
        val text = "-- café\n" + "print('hello world')\n".repeat(200)
        assertFailsWith<InputError> { adapter.writeLuaEntry(WriteLuaEntryRequest(output, luaTgi, text)) }
        val added = adapter.writeLuaEntry(WriteLuaEntryRequest(output, luaTgi, text, merge = true))
        assertEquals(2, added.entryCount)
        assertTrue(added.bytesWritten > 0)
        assertEquals("Original", adapter.readLText(ReadLTextRequest(output, ltextTgi)).text)
        assertTrue(adapter.listEntries(ListEntriesRequest(output)).entries.single { it.tgi == luaTgi }.compressed != null)
        assertEquals(text, adapter.readLua(ReadLuaRequest(output, luaTgi)).text)
        assertEquals(201, adapter.readLua(ReadLuaRequest(output, luaTgi)).lineCount)
        assertTrue(adapter.explainEntry(ExplainEntryRequest(output, luaTgi)).importantFields.any {
            it.name == "preview" && it.value.startsWith("-- café")
        })
        val exported = tempDir.resolve("script.lua")
        adapter.exportLuaText(ExportLuaTextRequest(output, luaTgi, exported.toString()))
        assertEquals(text, Files.readString(exported))

        val updated = adapter.writeLText(
            WriteLTextRequest(
                outputPath = output,
                entries = listOf(LTextWriteEntry(ltextTgi, "Updated")),
                merge = true,
            ),
        )
        assertEquals(2, updated.entryCount)
        assertEquals(text, adapter.readLua(ReadLuaRequest(output, luaTgi)).text)
        val replaced = adapter.writeLuaEntry(
            WriteLuaEntryRequest(output, luaTgi, "return 99", compressed = false, merge = true),
        )
        assertEquals(2, replaced.entryCount)
        assertEquals("return 99", adapter.readLua(ReadLuaRequest(output, luaTgi)).text)
        assertEquals("Updated", adapter.readLText(ReadLTextRequest(output, ltextTgi)).text)
    }

    @Test
    fun `Lua line counts handle empty text and trailing line separators`() {
        val path = tempDir.resolve("lines.dat").toString()
        adapter.writeLText(WriteLTextRequest(path, listOf(LTextWriteEntry(ltextTgi, "Keep"))))
        for ((text, count) in listOf("" to 0, "return 1" to 1, "return 1\n" to 1, "return 1\r\n\r\n" to 2)) {
            adapter.writeLuaEntry(WriteLuaEntryRequest(path, luaTgi, text, compressed = false, merge = true))
            val result = adapter.readLua(ReadLuaRequest(path, luaTgi))
            assertEquals(text, result.text)
            assertEquals(count, result.lineCount)
        }
    }

    @Test
    fun `Lua rejects wrong types and invalid UTF-8`() {
        val path = tempDir.resolve("invalid.dat").toString()
        adapter.writeRawEntries(
            WriteRawEntriesRequest(
                outputPath = path,
                entries = listOf(RawWriteEntry(luaTgi, Base64.getEncoder().encodeToString(byteArrayOf(0xFF.toByte())))),
                compressed = false,
            ),
        )
        assertFailsWith<DecodeError> { adapter.readLua(ReadLuaRequest(path, luaTgi)) }
        assertEquals(
            listOf("LUA payload does not look like UTF-8 source text; it may be a binary Lua chunk."),
            adapter.explainEntry(ExplainEntryRequest(path, luaTgi)).warnings,
        )
        assertFailsWith<InputError> { adapter.writeLuaEntry(WriteLuaEntryRequest(path, ltextTgi, "return 1")) }
    }
}
