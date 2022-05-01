import com.github.reviversmc.themodindex.creator.core.apicalls.CurseForgeApiCall
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthApiCall
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse
import com.github.reviversmc.themodindex.creator.core.apicalls.apiCallModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.junit5.KoinTestExtension
import java.util.Properties
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApiCallTest : KoinTest {

    private val curseForgeApiCall by inject<CurseForgeApiCall> {
        parametersOf(
            {
                val properties = Properties()
                properties.load(
                    this.javaClass.getResourceAsStream("/curseforge_api_key.txt")
                        ?: return@parametersOf System.getenv("CURSEFORGE_API_KEY") ?: "broken-api-key"
                )
                return@parametersOf properties.getProperty("api_key") ?: System.getenv()["CURSEFORGE_API_KEY"]
                ?: "broken-api-key"
            }
        )
    }

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
     * @since 1.0.0-1.0.0
     */
    @Test
    fun `modrinth api test`() {
        val modrinthApiCall by inject<ModrinthApiCall>()

        //Lowercase all the fields we can for reliability. Those not lowercase are from case sensitive fields (e.g. id)
        val sodiumProject = modrinthApiCall.project("sodium")
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
