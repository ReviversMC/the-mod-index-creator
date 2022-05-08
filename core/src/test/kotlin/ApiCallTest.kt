import com.github.reviversmc.themodindex.creator.core.apicalls.*
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.junit5.KoinTestExtension
import java.util.Properties
import kotlin.test.*

class ApiCallTest : KoinTest {

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create {
        modules( //We be testing the default api call classes, therefore no custom module needed
            apiCallModule, dependencyModule
        )
    }

    /**
     * Uses Sodium by jellysquid to test the API call.
     *
     * @author ReviversMC
     * @since 1.0.0
     */
    @Test
    fun `curse api test`(koin: Koin) {
        assertThrows<SerializationException> {
            (koin.get<CurseForgeApiCall> { parametersOf("broken-api-key") }).mod(394468)
        }

        fun curseApiKey(): String {
            val properties = Properties()
            properties.load(
                this.javaClass.getResourceAsStream("/curseForgeApiKey.properties")
                    ?: return System.getenv("CURSEFORGE_API_KEY") ?: "broken-api-key"
            )
            return properties.getProperty("api_key") ?: System.getenv()["CURSEFORGE_API_KEY"]
            ?: "broken-api-key"
        }

        val curseForgeApiCall by inject<CurseForgeApiCall> {
            parametersOf(curseApiKey())
        }

        val sodiumProject = curseForgeApiCall.mod(394468)!!.data
        assertNotNull(sodiumProject)
        assertEquals("sodium", sodiumProject.name.lowercase())
        assertEquals("https://github.com/caffeinemc/sodium-fabric", sodiumProject.links.sourceUrl.lowercase())
        //Other urls left out as assumed to be working since source url is working
        assertContains(
            sodiumProject.authors,
            CurseForgeResponse.ModResponse.ModAuthor(
                302655,
                "jellysquid3_",
                "https://www.curseforge.com/members/28746583-jellysquid3_?username=jellysquid3_"
            )
        )
        assertNotNull(sodiumProject.allowModDistribution)
        assertFalse(sodiumProject.allowModDistribution ?: false) //Sodium has this set on false

        assertEquals(emptyList(), curseForgeApiCall.files(394468, CurseForgeApiCall.ModLoaderType.FORGE)!!.data)

        //Even though mod distribution is off, files are still returned
        val sodiumFiles = curseForgeApiCall.files(394468)
        assertNotNull(sodiumFiles)

        //Checks for
        assertContains(
            sodiumFiles.data,
            CurseForgeResponse.FileResponse(
                3669187,
                true,
                "Sodium mc1.18.2-0.4.1",
                listOf(
                    CurseForgeResponse.FileResponse.FileHash("f839863a6be7014b8d80058ea1f361521148d049", 1),
                    CurseForgeResponse.FileResponse.FileHash("601f5c1d8b2b6e3c08a1216000099508", 2),
                ),
                "https://edge.forgecdn.net/files/3669/187/sodium-fabric-mc1.18.2-0.4.1+build.15.jar",
                listOf("Fabric", "1.18.2")
            ),
        )

        //We did not specify max files, so we should get all files
        assertEquals(sodiumFiles.pagination.resultCount, sodiumFiles.pagination.totalCount)
    }

    /**
     * Uses Sodium by jellysquid to test the API call.
     *
     * @author ReviversMC
     * @since 1.0.0
     */
    @Test
    fun `modrinth api test`() {
        val modrinthApiCall by inject<ModrinthApiCall>()

        //Lowercase all the fields we can for reliability. Those not lowercase are from case sensitive fields (e.g. id)
        val sodiumProject = modrinthApiCall.project("AANobbMI")
        assertNotNull(sodiumProject)
        assertEquals("AANobbMI", sodiumProject.id)
        assertEquals("sodium", sodiumProject.title.lowercase())
        assertEquals("lgpl-3", sodiumProject.license!!.id.lowercase())
        assertEquals("https://github.com/caffeinemc/sodium-fabric", sodiumProject.sourceUrl!!.lowercase())
        //Other urls left out as assumed to be working since source url is working
        assertContains(sodiumProject.versions, "74Y5Z8fo") //This version is v0.4.1

        val sodiumVersion041 = modrinthApiCall.version("74Y5Z8fo")
        assertNotNull(sodiumVersion041)
        assertEquals("sodium 0.4.1", sodiumVersion041.name.lowercase())
        assertEquals(listOf("1.18.2"), sodiumVersion041.gameVersions)
        assertEquals(listOf("fabric"), sodiumVersion041.loaders.map { it.lowercase() })
        assertContains(
            sodiumVersion041.files,
            ModrinthResponse.VersionResponse.VersionFile(
                ModrinthResponse.VersionResponse.VersionFile.VersionHash("f839863a6be7014b8d80058ea1f361521148d049"),
                "https://cdn.modrinth.com/data/AANobbMI/versions/mc1.18.2-0.4.1/sodium-fabric-mc1.18.2-0.4.1%2Bbuild.15.jar",
                true
            )
        )

        assertEquals("jellysquid3", modrinthApiCall.projectOwner("AANobbMI")!!.lowercase())
    }


}
