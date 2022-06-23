import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.apicalls.CurseForgeApiCall
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import io.mockk.mockkClass
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.junit5.KoinTestExtension
import org.koin.test.junit5.mock.MockProviderExtension
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreationTest : KoinTest {

    // TODO Mock GitHub API, and finish fake CF server
    /*
    We CANNOT decode (json) string inputs to a Kotlin object, as that would remove unrecognized fields.
    We want the json with unrecognized fields to be sent as an api response.
    */
    private val baseUrl = "http://localhost" // Ensure not https!
    private val curseForgeModId = 463481
    private val modrinthProjectId = "2NpFE0R3"

    private val curseForgeModResponse = readResource("/apiResponse/curseForge/curseMod.json")

    private val modrinthProjectResponse = readResource("/apiResponse/modrinth/modrinthProject.json")
    private val modrinthTeamResponse = readResource("/apiResponse/modrinth/modrinthTeam.json")
    private val modrinthVersionResponse = readResource("/apiResponse/modrinth/modrinthVersions.json")

    private var isCurseForgeDistribution = true
    private var isTestingForGitHub = false

    @Suppress("KotlinConstantConditions") // We want to use the curse number constants
    private fun curseForgeFilesResponse(curseNumber: Int = 0) = when (curseNumber) {
        CurseForgeApiCall.ModLoaderType.ANY.curseNumber -> readResource("/apiResponse/curseForge/curseAllFiles.json")
        CurseForgeApiCall.ModLoaderType.FABRIC.curseNumber -> readResource("/apiResponse/curseForge/curseFabricFiles.json")
        CurseForgeApiCall.ModLoaderType.QUILT.curseNumber -> readResource("/apiResponse/curseForge/curseQuiltFiles.json")
        else -> readResource("/apiResponse/curseForge/curseEmptyFiles.json")
    }


    private val curseForgeServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("x-api-key") == null) return MockResponse().setResponseCode(401)

                return when (request.path?.substringBefore("?")) {
                    "/v1/mods/$curseForgeModId" -> MockResponse().setResponseCode(200)
                        .setBody(if (isTestingForGitHub) curseForgeModResponse else buildString {
                            curseForgeModResponse.split("\n").forEach {
                                if (it.contains("\"sourceUrl\":")) {
                                    append("${it.substringBefore("\"sourceUrl\":")}\"sourceUrl\": null\n")
                                } else append("$it\n")
                            }
                        })

                    "/v1/mods/$curseForgeModId/files" -> MockResponse().setResponseCode(200).setBody(buildString {
                        curseForgeFilesResponse( // Get the modLoaderType path param
                            request.path!!.substringAfter("?").split("&").first { it.startsWith("modLoaderType=") }
                                .substringAfter("modLoaderType=").toInt()
                        ).split("\n").forEach {
                            if (it.contains("\"downloadUrl\"")) {
                                if (isCurseForgeDistribution) append(
                                    it.replace(
                                        "{port}", downloadServer.port.toString()
                                    )
                                )
                                else append("${it.substringBefore("\"downloadUrl\"")}\"downloadUrl\": null,\n")
                            } else append("$it\n")
                        }
                    })

                    else -> MockResponse().setResponseCode(404).setBody(request.path.toString())
                }
            }
        }
    }

    private val modrinthServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path?.substringBefore("?")) {
                    "/v2/project/$modrinthProjectId" -> MockResponse().setResponseCode(200)
                        .setBody(if (isTestingForGitHub) modrinthProjectResponse else buildString {
                            modrinthProjectResponse.split("\n").forEach {
                                if (it.contains("\"source_url\":")) {
                                    append("${it.substringBefore("\"source_url\":")}\"source_url\": null,\n")
                                } else append("$it\n")
                            }
                        })

                    "/v2/project/$modrinthProjectId/members" -> MockResponse().setResponseCode(200)
                        .setBody(modrinthTeamResponse)
                    "/v2/project/$modrinthProjectId/version" -> MockResponse().setResponseCode(200)
                        .setBody(modrinthVersionResponse)
                    else -> MockResponse().setResponseCode(404).setBody(request.path.toString())
                }
            }
        }
    }

    private val downloadServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.path?.startsWith("/files/") == true) {
                    MockResponse().setResponseCode(200).setBody(
                        readResource(
                            "/apiResponse/downloadableFiles/${request.path!!.removePrefix("/files/").removeSuffix("/")}"
                        )
                    )
                } else MockResponse().setResponseCode(404).setBody(request.path.toString())
            }
        }
    }

    private fun readResource(path: String) =
        javaClass.getResource(path)?.readText() ?: throw NoSuchFileException(file = File(path))


    @BeforeEach
    fun `before each`() {
        curseForgeServer.start()
        downloadServer.start()
        modrinthServer.start()
    }

    @AfterEach
    fun `after each`() {
        curseForgeServer.shutdown()
        downloadServer.shutdown()
        modrinthServer.shutdown()
    }

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create { modules(fakeCreatorModule) }

    @JvmField
    @RegisterExtension
    val mockProvider = MockProviderExtension.create { mockkClass(it) }


    /**
     * Checks if there is the same manifest between the file in resources and the one generated by the creator.
     * [testType] needs to be specified to determine which manifest to compare to (e.g. "pureModrinth, "pureCurseForge")
     */
    private fun assertManifestEquals(testType: String, manifest: ManifestJson) = assertEquals(
        get<Json>().decodeFromString(
            readResource(
                "/expectedManifest/$testType/${
                    manifest.genericIdentifier.substringBefore(
                        ":"
                    )
                }/${manifest.genericIdentifier.substringAfter(":")}.json"
            )
        ), manifest
    )

    @Test
    fun `creator methods should be equivalent`() = get<Creator> {
        parametersOf(
            "", "$baseUrl:${curseForgeServer.port}/", "", "$baseUrl:${modrinthServer.port}/"
        )
    }.let { creator ->
        assertTrue {
            creator.createManifestCurseForge(
                curseForgeModId,
                modrinthProjectId
            ) == creator.createManifestModrinth(modrinthProjectId, curseForgeModId, false)
        }

        assertTrue {
            creator.createManifestCurseForge(
                curseForgeModId,
                modrinthProjectId,
                false
            ) == creator.createManifestModrinth(modrinthProjectId, curseForgeModId)
        }
    }

    @Test
    fun `generate manifests without GitHub, Curse disabled`() {

        val creator = get<Creator> {
            parametersOf(
                "", "$baseUrl:${curseForgeServer.port}/", "", "$baseUrl:${modrinthServer.port}/"
            )
        }

        isCurseForgeDistribution = false
        isTestingForGitHub = false

        creator.createManifestModrinth(modrinthProjectId).run pureModrinth@{
            assertEquals(listOf(ThirdPartyApiUsage.MODRINTH_USED), thirdPartyApiUsage)
            assertEquals(2, manifests.size)
            manifests.forEach { assertManifestEquals("pureModrinth", it) }
        }

        creator.createManifestCurseForge(curseForgeModId).run curseForgeDisabled@{
            assertEquals(thirdPartyApiUsage, listOf(ThirdPartyApiUsage.CURSEFORGE_USED))
            assertEquals(0, manifests.size)
            assertEquals(
                emptyList(),
                manifests
            ) // No manifests should be generated, as all files are disabled.
        }

        creator.createManifestCurseForge(curseForgeModId, modrinthProjectId).run curseDisabledPlusModrinth@{
            assertEquals(
                listOf(ThirdPartyApiUsage.CURSEFORGE_USED, ThirdPartyApiUsage.MODRINTH_USED),
                thirdPartyApiUsage.sorted()
            )
            assertEquals(2, manifests.size)
            manifests.forEach { assertManifestEquals("curseDisabledPlusModrinth", it) }
        }

        creator.createManifestModrinth(modrinthProjectId, curseForgeModId).run modrinthPlusCurseForgeDisabled@{
            assertEquals(
                listOf(ThirdPartyApiUsage.CURSEFORGE_USED, ThirdPartyApiUsage.MODRINTH_USED),
                thirdPartyApiUsage.sorted()
            )
            assertEquals(2, manifests.size)
            manifests.forEach { assertManifestEquals("modrinthPlusCurseDisabled", it) }
        }
    }

    @Test
    fun `generate manifests without GitHub, Curse enabled`() {

        val creator = get<Creator> {
            parametersOf(
                "", "$baseUrl:${curseForgeServer.port}/", "", "$baseUrl:${modrinthServer.port}/"
            )
        }

        isCurseForgeDistribution = true
        isTestingForGitHub = false

        creator.createManifestModrinth(modrinthProjectId).run pureModrinth@{
            assertEquals(listOf(ThirdPartyApiUsage.MODRINTH_USED), thirdPartyApiUsage)
            assertEquals(2, manifests.size)
            manifests.forEach { assertManifestEquals("pureModrinth", it) }
        }

        creator.createManifestCurseForge(curseForgeModId).run pureCurseForge@{
            assertEquals(listOf(ThirdPartyApiUsage.CURSEFORGE_USED), thirdPartyApiUsage)
            assertEquals(2, manifests.size)
            manifests.forEach { assertManifestEquals("pureCurseForge", it) }
        }

        creator.createManifestCurseForge(curseForgeModId, modrinthProjectId).run curseEnabledPlusModrinth@{
            assertEquals(
                listOf(ThirdPartyApiUsage.CURSEFORGE_USED, ThirdPartyApiUsage.MODRINTH_USED),
                thirdPartyApiUsage.sorted()
            )
            assertEquals(2, manifests.size)
            manifests.forEach { assertManifestEquals("curseEnabledPlusModrinth", it) }
        }

        creator.createManifestModrinth(modrinthProjectId, curseForgeModId).run modrinthPlusCurseForgeEnabled@{
            assertEquals(
                listOf(ThirdPartyApiUsage.CURSEFORGE_USED, ThirdPartyApiUsage.MODRINTH_USED),
                thirdPartyApiUsage.sorted()
            )
            assertEquals(2, manifests.size)
            manifests.forEach { assertManifestEquals("modrinthPlusCurseEnabled", it) }
        }
    }

}