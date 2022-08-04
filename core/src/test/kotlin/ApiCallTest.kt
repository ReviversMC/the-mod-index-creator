import com.github.reviversmc.themodindex.creator.core.CreatorLoader
import com.github.reviversmc.themodindex.creator.core.apicalls.*
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.inject
import org.koin.test.junit5.KoinTestExtension
import java.io.IOException
import java.util.Properties
import kotlin.test.*

class ApiCallTest : KoinTest {

    @ExperimentalSerializationApi
    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create {
        modules( // We be testing the default api call classes, therefore no custom module needed
            apiCallModule
        )
    }

    /**
     * Uses Modget by ReviversMC to test the API call.
     *
     * @author ReviversMC
     * @since 1.0.0
     */
    @Test
    fun `curse api test`() {
        val curseForgeApiCall by inject<CurseForgeApiCall>()
        assertNull(curseForgeApiCall.mod("broken-api-key", 533960).execute().body())

        fun curseApiKey(): String {
            val properties = Properties()
            properties.load(
                this.javaClass.getResourceAsStream("/credentials.properties")
                    ?: return System.getenv("CURSEFORGE_API_KEY") ?: "broken-api-key"
            )
            return properties.getProperty("api_key") ?: System.getenv("CURSEFORGE_API_KEY") ?: "broken-api-key"
        }

        val curseForgeMod = curseForgeApiCall.mod(curseApiKey(), 533960).execute().body()!!.data
        assertNotNull(curseForgeMod)
        assertEquals("modget", curseForgeMod.name.lowercase())
        assertEquals("https://github.com/reviversmc/modget-minecraft", curseForgeMod.links.sourceUrl?.lowercase())
        // Other urls left out as assumed to be working since source url is working
        assertContains(
            curseForgeMod.authors, CurseModAuthor(
                452596, "NebelNidas", "https://www.curseforge.com/members/100230862-nebelnidas?username=nebelnidas"
            )
        )

        assertEquals(
            emptyList(), curseForgeApiCall.files(
                curseApiKey(), 533960, modLoaderType = CreatorLoader.CAULDRON.curseNumber
            ).execute().body()!!.data
        )

        val curseForgeFiles = curseForgeApiCall.files(curseApiKey(), 533960).execute().body()
        assertNotNull(curseForgeFiles)

        assertEquals(
            CurseFileResponse(
                3481563,
                true,
                "0.1.0 for MC 1.16",
                "https://edge.forgecdn.net/files/3481/563/modget-0.1.0.jar",
                listOf("Fabric", "1.16.5", "1.16.4"),
                listOf(
                    CurseFileDependency(400548, RelationType.EMBEDDED_LIBRARY.curseNumber),
                    CurseFileDependency(306612, RelationType.REQUIRED_DEPENDENCY.curseNumber)
                ).sortedBy { it.relationType }
            ), curseForgeFiles.data.last().let { cfFiles -> cfFiles.copy(dependencies = cfFiles.dependencies.sortedBy { it.relationType }) }
        )

        // We did not specify max files, so we should get all files
        assertTrue { curseForgeFiles.pagination.resultCount == curseForgeFiles.pagination.totalCount }

        val curseForgeSearch = curseForgeApiCall.search(curseApiKey(), 0, 1).execute().body()!!
        assertEquals(1, curseForgeSearch.data.size)
        assertEquals(1, curseForgeSearch.pagination.pageSize)
        assertEquals(1, curseForgeSearch.pagination.resultCount)
        assertEquals(0, curseForgeSearch.pagination.index)
    }

    /**
     * Uses Modget by ReviversMC to test the API call.
     *
     * @author ReviversMC
     * @since 1.0.0
     */
    @Test
    fun `modrinth api test`() {
        val modrinthApiCall by inject<ModrinthApiCall>()

        // Lowercase all the fields we can for reliability. Those not lowercase are from case-sensitive fields (e.g. id)
        val modrinthProject = modrinthApiCall.project("2NpFE0R3").execute().body()
        assertNotNull(modrinthProject)
        assertEquals("2NpFE0R3", modrinthProject.id)
        assertEquals("modget", modrinthProject.title.lowercase())
        assertEquals("lgpl-3", modrinthProject.license!!.id.lowercase())
        assertEquals("https://github.com/reviversmc/modget-minecraft", modrinthProject.sourceUrl!!.lowercase())

        val modrinthProjectSearch = modrinthApiCall.search("modget").execute().body()
        assertNotNull(modrinthProjectSearch)
        assertContains(modrinthProjectSearch.hits.map { it.id }, modrinthProject.id)
        assertContains(modrinthProjectSearch.hits.map { it.title }, modrinthProject.title)
        assertContains(modrinthProjectSearch.hits.map { it.license }, modrinthProject.license!!.id)

        val modgetVersion001 = modrinthApiCall.versions("2NpFE0R3").execute().body()!!.last()
        assertNotNull(modgetVersion001)
        assertEquals("0.0.1 for MC 1.16", modgetVersion001.name)
        assertEquals(emptyList(), modgetVersion001.dependencies)
        assertEquals(listOf("1.16.5", "1.16.4"), modgetVersion001.gameVersions.sortedDescending())
        assertEquals(listOf("fabric"), modgetVersion001.loaders.map { it.lowercase() })
        assertEquals(
            ModrinthVersionFile(
                ModrinthVersionHash("7dc82b00a305d8793a6787897f6c3bcf415e75ed0a257f17c40046320a1f3b686a1195eb3d4a3c36acd9b41308819315c2eb804194e44f5fe7fa303e5afc4fbc"),
                "https://cdn.modrinth.com/data/2NpFE0R3/versions/0.0.1/modget-0.0.1.jar",
            ), modgetVersion001.files.last()
        )

        val projectOwner = modrinthApiCall.projectMembers("2NpFE0R3").execute().body()
            ?.first { member -> member.role == "Owner" }?.userResponse?.username
            ?: throw IOException("No owner found for modrinth project: 2NpFE0R3")

        assertEquals("NebelNidas", projectOwner)
        assertContains(modrinthProjectSearch.hits.map { it.author }, projectOwner)
    }


}
