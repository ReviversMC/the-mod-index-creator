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

        val curseForgeMod = curseForgeApiCall.mod(394468)!!.data
        assertNotNull(curseForgeMod)
        assertEquals("sodium", curseForgeMod.name.lowercase())
        assertEquals("https://github.com/caffeinemc/sodium-fabric", curseForgeMod.links.sourceUrl.lowercase())
        //Other urls left out as assumed to be working since source url is working
        assertContains(
            curseForgeMod.authors,
            CurseForgeResponse.ModResponse.ModAuthor(
                302655,
                "jellysquid3_",
                "https://www.curseforge.com/members/28746583-jellysquid3_?username=jellysquid3_"
            )
        )

        assertEquals(emptyList(), curseForgeApiCall.files(394468, CurseForgeApiCall.ModLoaderType.FORGE)!!.data)

        val sodiumFiles = curseForgeApiCall.files(394468)
        assertNotNull(sodiumFiles)

        assertContains(
            sodiumFiles.data,
            CurseForgeResponse.FileResponse(
                3669187,
                true,
                "Sodium mc1.18.2-0.4.1",
                "https://edge.forgecdn.net/files/3669/187/sodium-fabric-mc1.18.2-0.4.1+build.15.jar",
                listOf("Fabric", "1.18.2")
            )
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

        val sodiumVersion010 = modrinthApiCall.versions("AANobbMI").last()
        assertNotNull(sodiumVersion010)
        assertEquals("Sodium 0.1.0", sodiumVersion010.name)
        assertEquals(listOf("1.16.5", "1.16.4", "1.16.3"), sodiumVersion010.gameVersions.sortedDescending())
        assertEquals(listOf("fabric"), sodiumVersion010.loaders.map { it.lowercase() })
        assertContains(
            sodiumVersion010.files,
            ModrinthResponse.VersionResponse.VersionFile(
                ModrinthResponse.VersionResponse.VersionFile.VersionHash("2597064f7116315adcfe114f0d76c04233791fed049cbbcf722f878002bf14cd8dec421806da639f31ffd496b9dedac9e39e2b48f43007427fa0f659ec5118e2"),
                "https://cdn.modrinth.com/data/AANobbMI/versions/mc1.16.3-0.1.0/sodium-fabric-mc1.16.3-0.1.0.jar",
                true
            )
        )

        assertEquals("jellysquid3", modrinthApiCall.projectOwner("AANobbMI")!!.lowercase())
    }


}
