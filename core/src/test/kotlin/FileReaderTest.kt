import cc.ekblad.toml.TomlMapper
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import com.github.reviversmc.themodindex.creator.core.filereader.FabricFile
import com.github.reviversmc.themodindex.creator.core.filereader.ForgeFile
import com.github.reviversmc.themodindex.creator.core.filereader.QuiltFile
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.junit5.KoinTestExtension
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals

class FileReaderTest: KoinTest {

    private val json by inject<Json>()
    private val toml by inject<TomlMapper>()

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create { modules(dependencyModule) }

    private fun createZipStream(filePath: String, zippedFileName: String): InputStream {
        val outputStream = ByteArrayOutputStream(1024)
        ZipOutputStream(outputStream).use { zos ->

            fun addFile(file: InputStream, name: String) {
                // Filter out the actual file name
                val dirs = name.substringBeforeLast("/", "").split("/", "").mapNotNull {
                    it.ifBlank { null }
                }
                for (dir in dirs) {
                    zos.putNextEntry(ZipEntry("$dir/"))
                    zos.closeEntry()
                }
                zos.putNextEntry(ZipEntry(name))
                file.let {
                    val transfer = it.readBytes()
                    zos.write(transfer.decodeToString().toByteArray())
                }
                // file.use { it.copyTo(zos, 1024) } // Else is dir
                zos.closeEntry()
            }

            addFile(
                javaClass.getResourceAsStream("/metadataFiles/fillerInfo.txt") ?: throw NoSuchFileException(
                    file = File(
                        "/metadataFiles/fillerInfo.txt"
                    )
                ), "fillerInfo.txt"
            )
            addFile(
                javaClass.getResourceAsStream(filePath) ?: throw NoSuchFileException(file = File(filePath)),
                zippedFileName
            )

        }
        return outputStream.toByteArray().inputStream()
    }

    @Test
    fun `test fabric reader`() {
        val currentFormat = FabricFile(
            createZipStream("/metadataFiles/fabric/fabric.mod.json", "fabric.mod.json"), json
        )
        assertEquals("modid", currentFormat.modId())
    }


    @Test
    fun `test forge reader`() {
        val legacyFormat = ForgeFile(
            createZipStream("/metadataFiles/forge/mcmod.info", "mcmod.info"), json, toml
        )
        val currentFormat = ForgeFile(
            createZipStream("metadataFiles/forge/mods.toml", "META-INF/mods.toml"), json, toml
        )
        assertEquals("examplemod", legacyFormat.modId())
        assertEquals("examplemod", currentFormat.modId())
    }

    @Test
    fun `test quilt reader`() {
        val currentFormat = QuiltFile(
            createZipStream("/metadataFiles/quilt/quilt.mod.json", "quilt.mod.json").readBytes(), json
        )

        val fabricSafeFormat = QuiltFile(
            createZipStream("/metadataFiles/fabric/fabric.mod.json", "fabric.mod.json").readBytes(), json
        )

        assertEquals("example_mod", currentFormat.modId())
        assertEquals("modid", fabricSafeFormat.modId())
    }

}
