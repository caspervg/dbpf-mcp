package com.github.caspervg.dbpfmcp.integration

import com.github.caspervg.dbpfmcp.backend.scdbpf.ScdbpfAdapter
import com.github.caspervg.dbpfmcp.core.DecodePropertyValueRequest
import com.github.caspervg.dbpfmcp.core.ExportCohortTextRequest
import com.github.caspervg.dbpfmcp.core.ExportExemplarTextRequest
import com.github.caspervg.dbpfmcp.core.ExportFshPngRequest
import com.github.caspervg.dbpfmcp.core.ExportSC4PathsJsonRequest
import com.github.caspervg.dbpfmcp.core.ExportSC4PathsTextRequest
import com.github.caspervg.dbpfmcp.core.ExplainEntryRequest
import com.github.caspervg.dbpfmcp.core.IndexPluginsRequest
import com.github.caspervg.dbpfmcp.core.IndexStatusRequest
import com.github.caspervg.dbpfmcp.core.InspectPackageRequest
import com.github.caspervg.dbpfmcp.core.InputError
import com.github.caspervg.dbpfmcp.core.KnownEntryKind
import com.github.caspervg.dbpfmcp.core.ReadCohortRequest
import com.github.caspervg.dbpfmcp.core.ReadCohortTextRequest
import com.github.caspervg.dbpfmcp.core.ListEntriesRequest
import com.github.caspervg.dbpfmcp.core.ReadExemplarRequest
import com.github.caspervg.dbpfmcp.core.ReadExemplarTextRequest
import com.github.caspervg.dbpfmcp.core.SummarizePackageRequest
import com.github.caspervg.dbpfmcp.core.ReadLTextRequest
import com.github.caspervg.dbpfmcp.core.ReadRawEntryRequest
import com.github.caspervg.dbpfmcp.core.SearchIndexRequest
import com.github.caspervg.dbpfmcp.core.Tgi
import io.github.memo33.passera.unsigned.UInt
import io.github.memo33.scdbpf.BufferedEntry
import io.github.memo33.scdbpf.`DbpfFile$` as ScDbpfFileObject
import io.github.memo33.scdbpf.`DbpfProperty$` as ScDbpfPropertyObject
import io.github.memo33.scdbpf.`Exemplar$` as ScExemplarObject
import io.github.memo33.scdbpf.DbpfType
import io.github.memo33.scdbpf.Exemplar
import io.github.memo33.scdbpf.DbpfProperty
import io.github.memo33.scdbpf.LText
import io.github.memo33.scdbpf.RawEntry
import io.github.memo33.scdbpf.RawType
import io.github.memo33.scdbpf.Tgi as ScTgi
import scala.Tuple2
import scala.Option
import scala.collection.IterableOnce
import scala.jdk.javaapi.CollectionConverters
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScdbpfAdapterIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `lists entries and decodes exemplar`() {
        val packagePath = tempDir.resolve("fixture.dat")
        val exemplarTgi = ScTgi.apply(0x6534284A, 0x00000000, 0x12345678)
        val cohortTgi = ScTgi.apply(0x05342861, 0x00000000, 0x12345679)
        val ltextTgi = ScTgi.apply(0x2026960B, 0x00000000, 0x1234567A)
        val props = CollectionConverters.asScala(
            listOf<Tuple2<UInt, DbpfProperty.PropertyList<*>>>(
                Tuple2(
                    UInt(0x20),
                    createProperty("Spike Exemplar"),
                ),
                Tuple2(
                    UInt(0x10),
                    createProperty(UInt(0x0B)),
                ),
                Tuple2(
                    UInt(0x27812850),
                    createProperty(UInt(3)),
                ),
            ),
        ) as IterableOnce<Tuple2<UInt, DbpfProperty.PropertyList<*>>>
        val exemplar = ScExemplarObject.`MODULE$`.apply(
            cohortTgi,
            false,
            props,
        )
        val cohort = ScExemplarObject.`MODULE$`.apply(
            ScTgi.Blank(),
            true,
            props,
        )
        val entries = listOf(
            BufferedEntry.apply(exemplarTgi, exemplar, true),
            BufferedEntry.apply(cohortTgi, cohort, true),
            BufferedEntry.apply(ltextTgi, LText.apply("Hello LTEXT"), true),
        )
        ScDbpfFileObject.`MODULE$`.write(
            CollectionConverters.asScala(entries),
            packagePath.toFile(),
            Option.empty(),
            Option.empty(),
            io.github.memo33.scdbpf.`package`.strategy().throwExceptions(),
        )

        val adapter = ScdbpfAdapter()
        val listResult = adapter.listEntries(ListEntriesRequest(path = packagePath.toString()))
        assertEquals(3, listResult.entryCount)
        assertEquals(3, listResult.entries.size)
        assertTrue(listResult.entries.first().compressed != null)

        val filteredListResult = adapter.listEntries(
            ListEntriesRequest(
                path = packagePath.toString(),
                kindFilter = com.github.caspervg.dbpfmcp.core.KnownEntryKind.EXEMPLAR,
                labelContains = "Exemplar",
            )
        )
        assertEquals(1, filteredListResult.entries.size)

        val exemplarResult = adapter.readExemplar(
            ReadExemplarRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x6534284AL, 0L, 0x12345678L),
            )
        )
        assertEquals("Spike Exemplar", exemplarResult.exemplarName)
        assertEquals(3, exemplarResult.properties.size)
        assertFalse(exemplarResult.properties.none { it.name == "Exemplar Name" })
        assertTrue(exemplarResult.properties.all { it.expectedType != null })
        assertEquals("exemplarType", exemplarResult.properties.first { it.id == 0x10L }.semanticType)

        val resolvedExemplarResult = adapter.readExemplar(
            ReadExemplarRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x6534284AL, 0L, 0x12345678L),
                resolveParent = true,
            )
        )
        assertEquals(Tgi(0x05342861L, 0L, 0x12345679L), resolvedExemplarResult.parentCohort)
        assertEquals(1, resolvedExemplarResult.parentChain.size)
        assertEquals("Spike Exemplar", resolvedExemplarResult.parentChain.first().name)
        assertTrue(resolvedExemplarResult.parentChain.first().resolved)

        val rawResult = adapter.readRawEntry(
            ReadRawEntryRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x6534284AL, 0L, 0x12345678L),
                maxBytes = 32,
            )
        )
        assertEquals(64, rawResult.payloadHexPreview.length)
        assertTrue(rawResult.payloadBase64.isNotBlank())
        assertTrue(rawResult.size > 0)

        val cohortResult = adapter.readCohort(
            ReadCohortRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x05342861L, 0L, 0x12345679L),
            )
        )
        assertEquals("Spike Exemplar", cohortResult.cohortName)
        assertEquals(3, cohortResult.properties.size)

        val ltextResult = adapter.readLText(
            ReadLTextRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x2026960BL, 0L, 0x1234567AL),
            )
        )
        assertEquals("Hello LTEXT", ltextResult.text)
        assertEquals(11, ltextResult.length)

        val exemplarExplanation = adapter.explainEntry(
            ExplainEntryRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x6534284AL, 0L, 0x12345678L),
            )
        )
        assertEquals(com.github.caspervg.dbpfmcp.core.KnownEntryKind.EXEMPLAR, exemplarExplanation.kind)
        assertTrue(exemplarExplanation.summary.contains("Spike Exemplar"))
        assertTrue(exemplarExplanation.importantFields.any { it.name == "objectClass" && it.value == "Network" })
        assertTrue(exemplarExplanation.relationships.any { it.kind == "parentCohort" })

        val ltextExplanation = adapter.explainEntry(
            ExplainEntryRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x2026960BL, 0L, 0x1234567AL),
            )
        )
        assertEquals(com.github.caspervg.dbpfmcp.core.KnownEntryKind.LTEXT, ltextExplanation.kind)
        assertTrue(ltextExplanation.importantFields.any { it.name == "preview" && it.value == "Hello LTEXT" })

        val propertyDescription = adapter.describeProperty(
            com.github.caspervg.dbpfmcp.core.DescribePropertyRequest(0x20)
        )
        assertEquals("Exemplar Name", propertyDescription.name)

        val packageSummary = adapter.summarizePackage(SummarizePackageRequest(path = packagePath.toString()))
        assertEquals(3, packageSummary.entryCount)
        assertEquals(true, packageSummary.countsByKind.any { it.kind == com.github.caspervg.dbpfmcp.core.KnownEntryKind.EXEMPLAR && it.count == 1 })

        val packageInspection = adapter.inspectPackage(
            InspectPackageRequest(
                path = packagePath.toString(),
                maxNotableEntries = 10,
                maxObjectHints = 10,
            )
        )
        assertEquals(3, packageInspection.entryCount)
        assertTrue(packageInspection.notableEntries.any { it.kind == com.github.caspervg.dbpfmcp.core.KnownEntryKind.EXEMPLAR })
        assertTrue(packageInspection.sc4ObjectHints.any { it.name == "Spike Exemplar" && it.exemplarType == "Network" })
        assertTrue(packageInspection.recommendedNextTools.contains("read_exemplar"))

        val decodedProperty = adapter.decodePropertyValue(
            DecodePropertyValueRequest(
                id = 0x00000010L,
                values = listOf(kotlinx.serialization.json.JsonPrimitive("0x0000000B")),
            )
        )
        assertEquals("Exemplar Type", decodedProperty.property.name)
        assertEquals("Network", decodedProperty.values.first().label)

        val exemplarText = adapter.readExemplarText(
            ReadExemplarTextRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x6534284AL, 0L, 0x12345678L),
            )
        )
        assertTrue(exemplarText.text.startsWith("EQZT1###"))
        assertTrue(exemplarText.text.contains("16:{\"Exemplar Type\"}=Uint32:0:{0x0000000B}"))
        val reparsedExemplar = parseTextExemplar(exemplarText.text, exemplarTgi)
        assertFalse(reparsedExemplar.isCohort())
        assertEquals(3, CollectionConverters.asJava(reparsedExemplar.properties()).size)

        val cohortText = adapter.readCohortText(
            ReadCohortTextRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x05342861L, 0L, 0x12345679L),
            )
        )
        assertTrue(cohortText.text.startsWith("CQZT1###"))
        val reparsedCohort = parseTextExemplar(cohortText.text, cohortTgi)
        assertTrue(reparsedCohort.isCohort())

        val exemplarTextPath = tempDir.resolve("exports/spike.exemplar.txt")
        val exemplarExport = adapter.exportExemplarText(
            ExportExemplarTextRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x6534284AL, 0L, 0x12345678L),
                outputPath = exemplarTextPath.toString(),
            )
        )
        assertTrue(exemplarExport.bytesWritten > 0)
        assertTrue(java.nio.file.Files.readString(exemplarTextPath).startsWith("EQZT1###"))

        val cohortTextPath = tempDir.resolve("exports/spike.cohort.txt")
        val cohortExport = adapter.exportCohortText(
            ExportCohortTextRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x05342861L, 0L, 0x12345679L),
                outputPath = cohortTextPath.toString(),
            )
        )
        assertTrue(cohortExport.bytesWritten > 0)
        assertTrue(java.nio.file.Files.readString(cohortTextPath).startsWith("CQZT1###"))
    }

    @Test
    fun `exports sc4paths text json and fsh png`() {
        val packagePath = tempDir.resolve("assets.dat")
        val sc4PathsTgi = ScTgi.apply(0x296678F7, 0x69668828, 0x03001A00)
        val fshTgi = ScTgi.apply(0x7AB50E44, 0x1ABE787D, 0x00000001)
        val sc4PathsText = """
            SC4PATHS
            1.1
            1
            1
            0
            -- Car_3_1
            1
            0
            3
            1
            2
            0.0,-8.0,0.0
            0.0,8.0,0.0
            -- Stop_Car_3_255
            1
            1
            0
            3
            255
            0.0,-7.0,0.0
        """.trimIndent().replace("\n", "\r\n")
        val entries = listOf(
            RawEntry(sc4PathsTgi, sc4PathsText.toByteArray(StandardCharsets.US_ASCII)),
            RawEntry(fshTgi, tinyFshA8R8G8B8()),
        )
        ScDbpfFileObject.`MODULE$`.write(
            CollectionConverters.asScala(entries),
            packagePath.toFile(),
            Option.empty(),
            Option.empty(),
            io.github.memo33.scdbpf.`package`.strategy().throwExceptions(),
        )

        val adapter = ScdbpfAdapter()
        val sc4Paths = adapter.readSC4Paths(
            com.github.caspervg.dbpfmcp.core.ReadSC4PathsRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x296678F7L, 0x69668828L, 0x03001A00L),
            )
        )
        assertEquals(1, sc4Paths.pathCount)
        assertEquals(1, sc4Paths.stopPathCount)

        val textModel = adapter.readSC4PathsText(
            com.github.caspervg.dbpfmcp.core.ReadSC4PathsRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x296678F7L, 0x69668828L, 0x03001A00L),
            )
        )
        assertTrue(textModel.text.startsWith("SC4PATHS"))

        val sc4PathsTextPath = tempDir.resolve("exports/path.sc4paths.txt")
        val textExport = adapter.exportSC4PathsText(
            ExportSC4PathsTextRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x296678F7L, 0x69668828L, 0x03001A00L),
                outputPath = sc4PathsTextPath.toString(),
            )
        )
        assertTrue(textExport.bytesWritten > 0)
        assertTrue(java.nio.file.Files.readString(sc4PathsTextPath).startsWith("SC4PATHS"))

        val sc4PathsJsonPath = tempDir.resolve("exports/path.sc4paths.json")
        val jsonExport = adapter.exportSC4PathsJson(
            ExportSC4PathsJsonRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x296678F7L, 0x69668828L, 0x03001A00L),
                outputPath = sc4PathsJsonPath.toString(),
            )
        )
        assertTrue(jsonExport.bytesWritten > 0)
        assertTrue(java.nio.file.Files.readString(sc4PathsJsonPath).contains("\"pathCount\""))

        val pngPath = tempDir.resolve("exports/texture.png")
        val pngExport = adapter.exportFshPng(
            ExportFshPngRequest(
                path = packagePath.toString(),
                tgi = Tgi(0x7AB50E44L, 0x1ABE787DL, 0x00000001L),
                outputPath = pngPath.toString(),
            )
        )
        assertTrue(pngExport.bytesWritten > 0)
        val pngBytes = java.nio.file.Files.readAllBytes(pngPath)
        assertEquals(0x89.toByte(), pngBytes[0])
        assertEquals('P'.code.toByte(), pngBytes[1])
        assertEquals('N'.code.toByte(), pngBytes[2])
        assertEquals('G'.code.toByte(), pngBytes[3])
    }

    @Test
    fun `indexes plugin folder and searches without reparsing caller path`() {
        val pluginsRoot = tempDir.resolve("Plugins")
        java.nio.file.Files.createDirectories(pluginsRoot)
        val packagePath = pluginsRoot.resolve("network-piece.dat")
        val exemplarTgi = ScTgi.apply(0x6534284A, 0x00000000, 0x87654321.toInt())
        val props = CollectionConverters.asScala(
            listOf<Tuple2<UInt, DbpfProperty.PropertyList<*>>>(
                Tuple2(UInt(0x20), createProperty("Indexed Network Piece")),
                Tuple2(UInt(0x10), createProperty(UInt(0x0B))),
            ),
        ) as IterableOnce<Tuple2<UInt, DbpfProperty.PropertyList<*>>>
        val exemplar = ScExemplarObject.`MODULE$`.apply(ScTgi.Blank(), false, props)
        ScDbpfFileObject.`MODULE$`.write(
            CollectionConverters.asScala(listOf(BufferedEntry.apply(exemplarTgi, exemplar, true))),
            packagePath.toFile(),
            Option.empty(),
            Option.empty(),
            io.github.memo33.scdbpf.`package`.strategy().throwExceptions(),
        )

        val adapter = ScdbpfAdapter()
        val index = adapter.indexPlugins(IndexPluginsRequest(rootPath = pluginsRoot.toString()))
        assertEquals(1, index.fileCount)
        assertEquals(1, index.entryCount)
        assertEquals(0, index.skippedFileCount)

        val status = adapter.indexStatus(IndexStatusRequest(rootPath = pluginsRoot.toString()))
        assertTrue(status.exists)
        assertEquals(1, status.fileCount)
        assertEquals(1, status.entryCount)
        assertEquals(0, status.staleIndexedFileCount)

        val nameSearch = adapter.searchIndex(
            SearchIndexRequest(
                rootPath = pluginsRoot.toString(),
                query = "indexed network",
            )
        )
        assertEquals(1, nameSearch.totalMatches)
        assertEquals("Indexed Network Piece", nameSearch.matches.first().exemplarName)

        val filteredSearch = adapter.searchIndex(
            SearchIndexRequest(
                rootPath = pluginsRoot.toString(),
                kindFilter = KnownEntryKind.EXEMPLAR,
                objectClass = "Network",
                propertyId = 0x20L,
            )
        )
        assertEquals(1, filteredSearch.totalMatches)
        assertEquals(Tgi(0x6534284AL, 0L, 0x87654321L), filteredSearch.matches.first().tgi)
    }

    @Test
    fun `rejects package file and plugin folder path misuse`() {
        val adapter = ScdbpfAdapter()
        val pluginsRoot = tempDir.resolve("Plugins")
        java.nio.file.Files.createDirectories(pluginsRoot)

        val folderError = assertFailsWith<InputError> {
            adapter.inspectPackage(InspectPackageRequest(path = pluginsRoot.toString()))
        }
        assertTrue(folderError.message.orEmpty().contains("Use index_plugins"))

        val packagePath = tempDir.resolve("not-a-folder.dat")
        java.nio.file.Files.writeString(packagePath, "DBPF")
        val fileError = assertFailsWith<InputError> {
            adapter.indexPlugins(IndexPluginsRequest(rootPath = packagePath.toString()))
        }
        assertTrue(fileError.message.orEmpty().contains("directory"))
    }

    private fun createProperty(value: Any): DbpfProperty.PropertyList<*> {
        val valueTypeModule = Class.forName("io.github.memo33.scdbpf.DbpfProperty\$ValueType\$")
            .getField("MODULE$")
            .get(null)
        val methodName = when (value) {
            is String -> "String"
            is UInt -> "Uint32"
            else -> error("Unsupported fixture property value: ${value::class.qualifiedName}")
        }
        val valueType = valueTypeModule.javaClass.getMethod(methodName).invoke(valueTypeModule)
        val applyMethod = ScDbpfPropertyObject.`MODULE$`.javaClass.methods
            .first { method -> method.name == "apply" && method.parameterTypes.size == 2 }
        return applyMethod.invoke(ScDbpfPropertyObject.`MODULE$`, value, valueType) as DbpfProperty.PropertyList<*>
    }

    private fun parseTextExemplar(text: String, tgi: ScTgi): Exemplar {
        val rawType = RawType.apply(text.toByteArray(StandardCharsets.US_ASCII))
        val entry = BufferedEntry.apply(tgi, rawType, false) as BufferedEntry<DbpfType>
        return (entry.convert(
            io.github.memo33.scdbpf.`package`.strategy().throwExceptions(),
            ScExemplarObject.`MODULE$`.converter(),
        ) as BufferedEntry<Exemplar>).content()
    }

    private fun tinyFshA8R8G8B8(): ByteArray {
        val bytes = ByteArray(0x2C)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(magic("SHPI"))
        buffer.putInt(bytes.size)
        buffer.putInt(1)
        buffer.putInt(magic("G264"))
        buffer.putInt(magic("BMP1"))
        buffer.putInt(0x18)
        buffer.put(0x7D.toByte())
        buffer.put(0x14)
        buffer.put(0)
        buffer.put(0)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.putInt(0xFFFF0000.toInt())
        return bytes
    }

    private fun magic(value: String): Int =
        value.encodeToByteArray().foldRight(0) { byte, acc -> (byte.toInt() and 0xFF) + 0x100 * acc }
}
