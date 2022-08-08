import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import com.github.reviversmc.themodindex.creator.core.modreader.ModFile
import com.github.reviversmc.themodindex.creator.core.modreader.modReaderModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.junit5.KoinTestExtension
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals

class FileReaderTest : KoinTest {

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create { modules(dependencyModule, modReaderModule) }

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
        val currentFormat = get<ModFile> {
            parametersOf(createZipStream("/metadataFiles/fabric/fabric.mod.json", "fabric.mod.json"))
        }
        assertEquals("modid", currentFormat.modId())
    }

    @Test
    fun `test legacy forge reader`() {
        val legacyFormat = get<ModFile> {
            parametersOf(createZipStream("/metadataFiles/forge/mcmod.info", "mcmod.info"))
        }
        assertEquals("examplemod", legacyFormat.modId())
    }

    @Test
    fun `test forge reader`() {
        val currentFormat = get<ModFile> {
            parametersOf(createZipStream("/metadataFiles/forge/mods.toml", "META-INF/mods.toml"))
        }
        assertEquals("examplemod", currentFormat.modId())
    }

    @Test
    fun `test liteloader reader`() {
        val currentFormat = get<ModFile> {
            parametersOf(createZipStream("/metadataFiles/liteloader/litemod.json", "litemod.json"))
        }
        assertEquals("Example", currentFormat.modId())
    }


    @Test
    fun `test quilt reader`() {
        val currentFormat = get<ModFile> {
            parametersOf(createZipStream("/metadataFiles/quilt/quilt.mod.json", "quilt.mod.json"))
        }

        val fabricSafeFormat = get<ModFile> {
            parametersOf(createZipStream("/metadataFiles/fabric/fabric.mod.json", "fabric.mod.json"))
        }

        assertEquals("example_mod", currentFormat.modId())
        assertEquals("modid", fabricSafeFormat.modId())
    }

    @Test
    fun `test rift reader`() {
        val currentFormat = get<ModFile> {
            parametersOf(createZipStream("/metadataFiles/rift/riftmod.json", "riftmod.json"))
        }
        assertEquals("halflogs", currentFormat.modId())
    }


}
