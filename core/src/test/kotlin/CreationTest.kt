import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.CreatorLoader
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
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

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create { modules(fakeCreatorModule) }

    @JvmField
    @RegisterExtension
    val mockProvider = MockProviderExtension.create { mockkClass(it) }

    // TODO Mock GitHub API
    /*
    We CANNOT decode (json) string inputs to a Kotlin object, as that would remove unrecognized fields.
    We want the json with unrecognized fields to be sent as an api response.
    */
    private val baseUrl = "http://localhost" // Ensure not https!

    private val curseForgeConsumerModId = 463481
    private val modrinthConsumerProjectId = "2NpFE0R3"
    private val curseForgeConsumerModResponse = readResource("/apiResponse/tmi-consumer/curseForge/curseMod.json")
    private val modrinthConsumerProjectResponse =
        readResource("/apiResponse/tmi-consumer/modrinth/modrinthProject.json")
    private val modrinthConsumerTeamResponse = readResource("/apiResponse/tmi-consumer/modrinth/modrinthTeam.json")
    private val modrinthConsumerVersionResponse =
        readResource("/apiResponse/tmi-consumer/modrinth/modrinthVersions.json")

    private val curseForgeBridgeModId = 1234
    private val modrinthBridgeProjectId = "1A3BC"
    private val curseForgeBridgeModResponse = readResource("/apiResponse/tmi-bridge/curseForge/curseMod.json")
    private val modrinthBridgeProjectResponse = readResource("/apiResponse/tmi-bridge/modrinth/modrinthProject.json")
    private val modrinthBridgeVersionResponse = readResource("/apiResponse/tmi-bridge/modrinth/modrinthVersions.json")

    private val curseForgeEaterModId = 5678
    private val modrinthEaterProjectId = "2A3BC"
    private val curseForgeEaterModResponse = readResource("/apiResponse/tmi-eater/curseForge/curseMod.json")
    private val modrinthEaterProjectResponse = readResource("/apiResponse/tmi-eater/modrinth/modrinthProject.json")
    private val modrinthEaterVersionResponse = readResource("/apiResponse/tmi-eater/modrinth/modrinthVersions.json")
    private val modrinthEaterSpecificVersionResponse =
        readResource("/apiResponse/tmi-eater/modrinth/modrinthVersion.json")

    private var isCurseForgeDistribution = true
    private var isTestingForGitHub = false

    private fun curseConsumerFilesResponse(curseNumber: Int?) = when (curseNumber) {
        CreatorLoader.ANY.curseNumber -> readResource("/apiResponse/tmi-consumer/curseForge/curseAllFiles.json")
        CreatorLoader.FABRIC.curseNumber -> readResource("/apiResponse/tmi-consumer/curseForge/curseFabricFiles.json")
        CreatorLoader.QUILT.curseNumber -> readResource("/apiResponse/tmi-consumer/curseForge/curseQuiltFiles.json")
        else -> readResource("/apiResponse/tmi-consumer/curseForge/curseEmptyFiles.json")
    }


    private val curseForgeServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("x-api-key") == null) return MockResponse().setResponseCode(401)

                return when (request.path?.substringBefore('?')) {
                    "/v1/mods/$curseForgeConsumerModId" -> MockResponse().setResponseCode(200)
                        .setBody(if (isTestingForGitHub) curseForgeConsumerModResponse else buildString {
                            curseForgeConsumerModResponse.split("\n").forEach {
                                if (it.contains("\"sourceUrl\":")) {
                                    append("${it.substringBefore("\"sourceUrl\":")}\"sourceUrl\": null\n")
                                } else append("$it\n")
                            }
                        })

                    "/v1/mods/$curseForgeConsumerModId/files" -> MockResponse().setResponseCode(200)
                        .setBody(buildString {
                            curseConsumerFilesResponse( // Get the modLoaderType path param
                                request.path!!.substringAfter('?').split('&').firstOrNull { it.startsWith("modLoaderType=") }
                                    ?.substringAfter("modLoaderType=")?.toIntOrNull()).split("\n").forEach {
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

                    "/v1/mods/$curseForgeBridgeModId" -> MockResponse().setResponseCode(200)
                        .setBody(curseForgeBridgeModResponse)

                    "/v1/mods/$curseForgeBridgeModId/files" -> {
                        if (request.path!!.substringAfter('?') == "modLoaderType=5") {
                            MockResponse().setResponseCode(200)
                                .setBody(readResource("/apiResponse/tmi-bridge/curseForge/curseQuiltFiles.json").replace("{port}", downloadServer.port.toString()))
                        } else MockResponse().setResponseCode(404)
                    }

                    "/v1/mods/$curseForgeEaterModId" -> MockResponse().setResponseCode(200)
                        .setBody(curseForgeEaterModResponse)

                    "/v1/mods/$curseForgeEaterModId/files" -> {
                        if (request.path!!.substringAfter('?') == "modLoaderType=5") {
                            MockResponse().setResponseCode(200)
                                .setBody(readResource("/apiResponse/tmi-eater/curseForge/curseQuiltFiles.json").replace("{port}", downloadServer.port.toString()))
                        } else MockResponse().setResponseCode(404)
                    }

                    else -> MockResponse().setResponseCode(404).setBody(request.path.toString())
                }
            }
        }
    }

    private val modrinthServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("User-Agent") == null) return MockResponse().setResponseCode(401)
                if (!request.getHeader("User-Agent")!!
                        .startsWith("reviversmc/the-mod-index-creator/", true)
                ) return MockResponse().setResponseCode(403)

                return when (request.path) {
                    "/v2/project/$modrinthConsumerProjectId" -> MockResponse().setResponseCode(200)
                        .setBody(if (isTestingForGitHub) modrinthConsumerProjectResponse else buildString {
                            modrinthConsumerProjectResponse.split("\n").forEach {
                                if (it.contains("\"source_url\":")) {
                                    append("${it.substringBefore("\"source_url\":")}\"source_url\": null,\n")
                                } else append("$it\n")
                            }
                        })

                    "/v2/project/$modrinthConsumerProjectId/members" -> MockResponse().setResponseCode(200)
                        .setBody(modrinthConsumerTeamResponse)

                    "/v2/project/$modrinthConsumerProjectId/version" -> MockResponse().setResponseCode(200)
                        .setBody(modrinthConsumerVersionResponse.replace("{port}", downloadServer.port.toString()))

                    "/v2/project/$modrinthBridgeProjectId" -> MockResponse().setResponseCode(200)
                        .setBody(modrinthBridgeProjectResponse)

                    "/v2/project/$modrinthBridgeProjectId/version?loaders=%5B%22quilt%22%5D" -> MockResponse().setResponseCode(
                        200
                    ).setBody(modrinthBridgeVersionResponse.replace("{port}", downloadServer.port.toString()))

                    "/v2/project/$modrinthEaterProjectId" -> MockResponse().setResponseCode(200)
                        .setBody(modrinthEaterProjectResponse)

                    "/v2/project/$modrinthEaterProjectId/version?loaders=%5B%22quilt%22%5D" -> MockResponse().setResponseCode(
                        200
                    ).setBody(modrinthEaterVersionResponse.replace("{port}", downloadServer.port.toString()))

                    "/v2/version/e2e3f" -> MockResponse().setResponseCode(200)
                        .setBody(modrinthEaterSpecificVersionResponse.replace("{port}", downloadServer.port.toString()))

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
                            "/apiResponse/tmi-consumer/downloadableFiles/${
                                request.path!!.removePrefix("/files/").removeSuffix("/")
                            }"
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


    /**
     * Checks if there is the same manifest between the file in resources and the one generated by the creator.
     * [testType] needs to be specified to determine which manifest to compare to (e.g. "pureModrinth, "pureCurseForge")
     */
    private fun assertManifestEquals(testType: String, manifest: ManifestJson) = assertEquals(
        get<Json>().decodeFromString(
            readResource(
                "/expectedManifest/tmi-consumer/$testType/${
                    manifest.genericIdentifier.substringBefore(
                        ":"
                    )
                }/${manifest.genericIdentifier.substringAfter(":")}.json"
            ).replace("{port}", downloadServer.port.toString())
        ), manifest
    )

    @Test
    fun `creator methods should be equivalent`() = runBlocking {
        get<Creator> {
            parametersOf(
                "", "$baseUrl:${curseForgeServer.port}/", "", "$baseUrl:${modrinthServer.port}/"
            )
        }.let { creator ->
            assertTrue {
                creator.createManifestCurseForge(
                    curseForgeConsumerModId, listOf(CreatorLoader.ANY), modrinthConsumerProjectId
                ) == creator.createManifestModrinth(
                    modrinthConsumerProjectId,
                    curseForgeConsumerModId,
                    listOf(CreatorLoader.ANY),
                    false
                )
            }

            assertTrue {
                creator.createManifestCurseForge(
                    curseForgeConsumerModId, listOf(CreatorLoader.ANY), modrinthConsumerProjectId, false
                ) == creator.createManifestModrinth(modrinthConsumerProjectId, curseForgeConsumerModId)
            }
        }
    }

    @Test
    fun `generate manifests without GitHub, Curse disabled`() = runBlocking {


        val creator = get<Creator> {
            parametersOf(
                "", "$baseUrl:${curseForgeServer.port}/", "", "$baseUrl:${modrinthServer.port}/"
            )
        }

        isCurseForgeDistribution = false
        isTestingForGitHub = false

        creator.createManifestModrinth(modrinthConsumerProjectId).run pureModrinth@{
            assertEquals(listOf(ThirdPartyApiUsage.MODRINTH_USED), thirdPartyApiUsage)
            assertEquals(2, manifests.size)
            manifests.forEach { assertManifestEquals("pureModrinth", it) }
        }

        creator.createManifestCurseForge(curseForgeConsumerModId, listOf(CreatorLoader.ANY)).run curseForgeDisabled@{
            assertEquals(thirdPartyApiUsage, listOf(ThirdPartyApiUsage.CURSEFORGE_USED))
            assertEquals(0, manifests.size)
            assertEquals(
                emptyList(), manifests
            ) // No manifests should be generated, as all files are disabled.
        }

        creator.createManifestCurseForge(curseForgeConsumerModId, listOf(CreatorLoader.ANY), modrinthConsumerProjectId)
            .run curseDisabledPlusModrinth@{
                assertEquals(
                    listOf(ThirdPartyApiUsage.CURSEFORGE_USED, ThirdPartyApiUsage.MODRINTH_USED),
                    thirdPartyApiUsage.sorted()
                )
                assertEquals(2, manifests.size)
                manifests.forEach { assertManifestEquals("curseDisabledPlusModrinth", it) }
            }

        creator.createManifestModrinth(modrinthConsumerProjectId, curseForgeConsumerModId)
            .run modrinthPlusCurseForgeDisabled@{
                assertEquals(
                    listOf(ThirdPartyApiUsage.CURSEFORGE_USED, ThirdPartyApiUsage.MODRINTH_USED),
                    thirdPartyApiUsage.sorted()
                )
                assertEquals(2, manifests.size)
                manifests.forEach { assertManifestEquals("modrinthPlusCurseDisabled", it) }
            }
    }

    @Test
    fun `generate manifests without GitHub, Curse enabled`() = runBlocking {

        val creator = get<Creator> {
            parametersOf(
                "", "$baseUrl:${curseForgeServer.port}/", "", "$baseUrl:${modrinthServer.port}/"
            )
        }

        isCurseForgeDistribution = true
        isTestingForGitHub = false

        creator.createManifestModrinth(modrinthConsumerProjectId).run pureModrinth@{
            assertEquals(listOf(ThirdPartyApiUsage.MODRINTH_USED), thirdPartyApiUsage)
            assertEquals(2, manifests.size)
            manifests.forEach { assertManifestEquals("pureModrinth", it) }
        }

        creator.createManifestCurseForge(curseForgeConsumerModId).run pureCurseForge@{
            assertEquals(listOf(ThirdPartyApiUsage.CURSEFORGE_USED), thirdPartyApiUsage)
            assertEquals(2, manifests.size)
            manifests.forEach { assertManifestEquals("pureCurseForge", it) }
        }

        creator.createManifestCurseForge(curseForgeConsumerModId, listOf(CreatorLoader.ANY), modrinthConsumerProjectId)
            .run curseEnabledPlusModrinth@{
                assertEquals(
                    listOf(ThirdPartyApiUsage.CURSEFORGE_USED, ThirdPartyApiUsage.MODRINTH_USED),
                    thirdPartyApiUsage.sorted()
                )
                assertEquals(2, manifests.size)
                manifests.forEach { assertManifestEquals("curseEnabledPlusModrinth", it) }
            }

        creator.createManifestModrinth(modrinthConsumerProjectId, curseForgeConsumerModId)
            .run modrinthPlusCurseForgeEnabled@{
                assertEquals(
                    listOf(ThirdPartyApiUsage.CURSEFORGE_USED, ThirdPartyApiUsage.MODRINTH_USED),
                    thirdPartyApiUsage.sorted()
                )
                assertEquals(2, manifests.size)
                manifests.forEach { assertManifestEquals("modrinthPlusCurseEnabled", it) }
            }
    }

}