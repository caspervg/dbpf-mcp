package com.github.caspervg.dbpfmcp.integration

import com.github.caspervg.dbpfmcp.backend.scdbpf.ScdbpfAdapter
import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.RawWriteEntry
import com.github.caspervg.dbpfmcp.core.ReadIniRequest
import com.github.caspervg.dbpfmcp.core.ReadRawEntryRequest
import com.github.caspervg.dbpfmcp.core.Tgi
import com.github.caspervg.dbpfmcp.core.WriteIniRequest
import com.github.caspervg.dbpfmcp.core.WriteRawEntriesRequest
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NetworkIniIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `writes reads and replaces Network INI entries in DBPF packages`() {
        val outputPath = tempDir.resolve("network-ini.dat")
        val adapter = ScdbpfAdapter()
        val iniTgi = Tgi(0x00000000L, 0x8A5971C5L, 0x8A5993B9L)
        val unrelatedTgi = Tgi(0xAA5C3144L, 0L, 0x77777777L)
        val unrelatedPayload = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
        adapter.writeRawEntries(
            WriteRawEntriesRequest(
                outputPath = outputPath.toString(),
                entries = listOf(RawWriteEntry(unrelatedTgi, unrelatedPayload)),
            ),
        )

        val initialText = """
            ; header comment
            [DebugRules]
            Reload=0
            [PowerPoles]
            0x0d=0x05000305
            [ShadowRemappings]
            default = 0,0,0,0
        """.trimIndent()
        val writeResult = adapter.writeIni(
            WriteIniRequest(
                outputPath = outputPath.toString(),
                tgi = iniTgi,
                text = initialText,
                merge = true,
            ),
        )
        assertEquals(2, writeResult.entryCount)
        assertEquals(iniTgi, writeResult.tgi)

        val readResult = adapter.readIni(ReadIniRequest(outputPath.toString(), iniTgi))
        assertEquals(initialText, readResult.text)
        assertEquals(initialText.toByteArray(StandardCharsets.UTF_8).size, readResult.size)
        assertEquals(iniTgi, readResult.tgi)
        assertEquals(
            unrelatedPayload,
            adapter.readRawEntry(ReadRawEntryRequest(outputPath.toString(), unrelatedTgi)).payloadBase64,
        )

        val replacementText = initialText.replace("Reload=0", "Reload=1")
        adapter.writeIni(WriteIniRequest(outputPath.toString(), iniTgi, replacementText, merge = true))
        assertEquals(replacementText, adapter.readIni(ReadIniRequest(outputPath.toString(), iniTgi)).text)

        assertFailsWith<InputError> {
            adapter.writeIni(WriteIniRequest(tempDir.resolve("empty.dat").toString(), iniTgi, ""))
        }
    }
}
