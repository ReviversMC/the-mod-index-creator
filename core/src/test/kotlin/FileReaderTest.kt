import cc.ekblad.toml.tomlMapper
import com.github.reviversmc.themodindex.creator.core.filereader.ForgeFile
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals

class FileReaderTest {

    private val json = Json { ignoreUnknownKeys = true }

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
    fun `test forge reader`() {
        val toml = tomlMapper { }
        val legacyFormat = ForgeFile(
            createZipStream("/metadataFiles/forge/mcmod.info", "mcmod.info"), json, toml
        )
        val currentFormat = ForgeFile(
            createZipStream("metadataFiles/forge/mods.toml", "META-INF/mods.toml"), json, toml
        )
        assertEquals("examplemod", legacyFormat.modId())
        assertEquals("examplemod", currentFormat.modId())
    }


}
