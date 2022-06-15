import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthProjectResponse
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
import kotlin.test.assertNotNull

class CreationTest : KoinTest {

    //TODO Mock GitHub API, and finish fake CF server

    private val baseUrl = "http://localhost" // Ensure not https!

    private val modrinthProjectResponse =
        this.javaClass.getResource("/TMIConsumer/modrinth/modrinthProject.json")?.readText()

    private val modrinthTeamResponse = this.javaClass.getResource("/TMIConsumer/modrinth/modrinthTeam.json")?.readText()

    private val modrinthVersionResponse =
        this.javaClass.getResource("/TMIConsumer/modrinth/modrinthVersion.json")?.readText()

    @Suppress("JSON_FORMAT_REDUNDANT") // We only use this instance once, and all other instances will be dependency injected.
    private val decodedModrinthProjectResponse =
        modrinthProjectResponse?.let { Json { ignoreUnknownKeys = true }.decodeFromString<ModrinthProjectResponse>(it) }

    private val projectId = decodedModrinthProjectResponse?.id ?: "0"
    private val versionId = decodedModrinthProjectResponse?.versions?.first() ?: "0"

    private val curseForgeServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.headers["x-api-key"] == null) return MockResponse().setResponseCode(401)

                return MockResponse().setResponseCode(500)
            }
        }
    }

    private val modrinthServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {

            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/v2/project/$projectId" -> modrinthProjectResponse?.let {
                        MockResponse().setResponseCode(
                            200
                        ).setBody(it)
                    } ?: MockResponse().setResponseCode(500)

                    "/v2/project/$projectId/members" -> modrinthTeamResponse?.let {
                        MockResponse().setResponseCode(
                            200
                        ).setBody(it)
                    } ?: MockResponse().setResponseCode(500)

                    "/v2/versions/$versionId" -> modrinthVersionResponse?.let {
                        MockResponse().setResponseCode(
                            200
                        ).setBody(it)
                    } ?: MockResponse().setResponseCode(500)

                    else -> MockResponse().setBody(request.path.toString()).setResponseCode(404)
                }
            }
        }
    }

    @BeforeEach
    fun `before each`() {
        curseForgeServer.start()
        modrinthServer.start()
    }

    @AfterEach
    fun `after each`() {
        curseForgeServer.start()
        modrinthServer.shutdown()
    }

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create {
        modules(fakeCreatorModule)
    }

    @Test
    fun `should return correct index`() {
        val creator = get<Creator> {
            parametersOf(
                "",
                "$baseUrl:${curseForgeServer.port}/",
                "",
                "$baseUrl:${modrinthServer.port}/"
            )
        }

        //TODO an actual useful test, that compares the actual output to the expected output
        assertNotNull(creator.createManifestModrinth(projectId))

    }

}