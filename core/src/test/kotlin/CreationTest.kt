import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.core.Creator
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

class CreationTest : KoinTest {

    // TODO Mock GitHub API, and finish fake CF server
    /*
    We CANNOT decode (json) string inputs to a Kotlin object, as that would remove unrecognized fields.
    We want the json with unrecognized fields to be sent as an api response.
    */
    private val baseUrl = "http://localhost" // Ensure not https!
    private val curseForgeModId = 463481
    private val modrinthProjectId = "2NpFE0R3"

    private val curseForgeModResponse = readResource("/ApiResponse/CurseForge/curseMod.json")
    private val curseForgeFilesResponse = readResource("/ApiResponse/CurseForge/curseFiles.json")

    private val modrinthProjectResponse = readResource("/ApiResponse/Modrinth/modrinthProject.json")
    private val modrinthTeamResponse = readResource("/ApiResponse/Modrinth/modrinthTeam.json")
    private val modrinthVersionResponse = readResource("/ApiResponse/Modrinth/modrinthVersions.json")

    private var isCurseForgeDistribution = true
    private var isTestingForGitHub = false


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

                    //TODO Respect path parameters
                    "/v1/mods/$curseForgeModId/files" -> MockResponse().setResponseCode(200).setBody(buildString {
                        curseForgeFilesResponse.split("\n").forEach {
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
                return if (request.path?.startsWith("http://localhost/files/") == true) {
                    MockResponse().setResponseCode(200).setBody(
                        readResource(
                            "/ApiResponse/DownloadableFiles/${request.path!!.removePrefix("http://localhost/files/")}"
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

    @Test
    fun `should generate accurate manifests`() {

        val creator = get<Creator> {
            parametersOf(
                "", "$baseUrl:${curseForgeServer.port}/", "", "$baseUrl:${modrinthServer.port}/"
            )
        }

        val json = get<Json>()

        /**
         * Checks if there is the same manifest between the file in resources and the one generated by the creator.
         * [testType] needs to be specified to determine which manifest to compare to (e.g. "pureModrinth, "pureCurseForge")
         */
        fun assertManifestEquals(testType: String, manifest: ManifestJson) {
            assertEquals(
                json.decodeFromString(
                    readResource(
                        "/ExpectedManifest/$testType/${
                            manifest.genericIdentifier.substringBefore(
                                ":"
                            )
                        }/${manifest.genericIdentifier.substringAfter(":")}.json"
                    )
                ), manifest
            )
        }

        isTestingForGitHub = false.apply {
            isCurseForgeDistribution = true.apply {
                val pureCurseForge = creator.createManifestCurseForge(curseForgeModId)
                assertEquals(pureCurseForge.thirdPartyApiUsage, listOf(ThirdPartyApiUsage.CURSEFORGE_USED))
                pureCurseForge.manifests.forEach { assertManifestEquals("pureCurseForge", it) }
            }

            val pureModrinth = creator.createManifestModrinth(modrinthProjectId)
            assertEquals(pureModrinth.thirdPartyApiUsage, listOf(ThirdPartyApiUsage.MODRINTH_USED))
            pureModrinth.manifests.forEach { assertManifestEquals("pureModrinth", it) }
        }


    }

}