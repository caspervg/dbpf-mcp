package com.github.caspervg.dbpfmcp.backend.scdbpf

import com.github.caspervg.dbpfmcp.core.Tgi
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabBinaryDecoderTest {
    @Test
    fun `decoder exposes little endian words for compiled tab sample`() {
        val bytes = Base64.getDecoder().decode(
            "3AAAABD7AAFGAwEAAAHgICACAAQA4DABAAABBxYBAygBAyABA0AEHwgAEAYEAAwtDAAfDP///wEDPAUDfwEYgAAD4OeAAAEEA+DPAAADAAMCFn+eBAPhHgAA4B+AAOABAw8RBwcBEx8BAz4BA3zh+AAA4fAAAOMBQuDgwAAA7wROAFYBA/4BA/wAHgEeAAEeAAEeAASDDAABA8MFA4ABlX8MAwkD/gMD///8AhKAARQDABeKAAMQFwEDAwEDBwEDDwEDHxFKPwBWAB4BHv8BHv8BHv8BHv8BHv/9/w==",
        )

        val model = decodeTabBinaryPayload(
            bytes = bytes,
            compressed = true,
            tgi = Tgi(0xAA5C3144L, 0x00000001L, 0x00000001L),
            maxWords = 16,
        )

        assertEquals("Structured binary TAB probe", model.formatHint)
        assertEquals("000000DC", model.headerWords.first())
        assertTrue(model.notes.any { it.contains("First word matches decompressed byte size") })
        assertEquals(16, model.words.size)
    }
}
