package com.github.reviversmc.themodindex.creator.ghapp

import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.ghapp.apicalls.githubGraphqlModule
import com.github.reviversmc.themodindex.creator.ghapp.data.AppConfig
import com.github.reviversmc.themodindex.creator.ghapp.github.UpdateSender
import com.github.reviversmc.themodindex.creator.ghapp.github.updateSenderModule
import com.github.reviversmc.themodindex.creator.ghapp.reviewer.ManifestReviewer
import com.github.reviversmc.themodindex.creator.ghapp.reviewer.manifestReviewModule
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

const val CURSEFORGE_API_KEY = ""
const val COROUTINES_PER_TASK = 5 // Arbitrary number of concurrent downloads. Change if better number is found.

private val logger = KotlinLogging.logger {}

private fun getOrCreateConfig(json: Json, location: String, exitIfCreate: Boolean = false): AppConfig {
    val configFile = File(location)
    if (configFile.exists() && configFile.isFile) {
        logger.info { "Found config file at $location" }
        return json.decodeFromString(configFile.readText())
    }

    logger.info { "No config file found at $location. Creating one now." }

    print("Please indicate your Discord bot token: \n > ")
    val botToken = readlnOrNull() ?: throw IOException("No token provided.")

    print("Please indicate the ID of the Discord channel to post to: \n > ")
    val channelId = readlnOrNull()?.toLongOrNull() ?: throw IOException("Invalid channel ID.")

    print("Please indicate the ID of the Discord server to post to: \n > ")
    val serverId = readlnOrNull()?.toLongOrNull() ?: throw IOException("Invalid server ID.")

    print("Please indicate the ID of the GitHub app to use: \n > ")
    val appId = readlnOrNull() ?: throw IOException("No GitHub App ID provided.")

    print("Please indicate the path of the GitHub app's private key: \n > ")
    val privateKey = readlnOrNull() ?: throw IOException("No private key provided.")

    print("Please indicate the GitHub owner of the manifest repository: \n > ")
    val owner = readlnOrNull() ?: throw IOException("No owner provided.")

    print("Please indicate name of the GitHub manifest repository: \n > ")
    val repoName = readlnOrNull() ?: throw IOException("No repository provided.")

    return AppConfig(botToken, channelId, serverId, appId, privateKey, owner, repoName).also {
        configFile.writeText(json.encodeToString(it))
        logger.info { "Config file created at ${configFile.absolutePath}." }

        if (exitIfCreate) {
            println("App exiting. Please relaunch the app to use the new config.")
            exitProcess(0)
        }
    }
}

fun main(args: Array<String>) {
    runBlocking {

        val koin = startKoin {
            modules(
                appModule, creatorModule, githubGraphqlModule, manifestReviewModule, updateSenderModule
            )
        }.koin

        val commandParser = koin.get<ArgParser>()

        val configLocation by commandParser.option(
            ArgType.String, shortName = "c", description = "The location of the config file"
        ).default("the-mod-index-automated-creator-config.json")

        val delayInHours by commandParser.option(
            ArgType.Int, shortName = "d", description = "How long to delay between updates"
        ).default(3)

        val sus by commandParser.option(
            ArgType.Boolean,
            shortName = "s",
            description = "Whether to be suspicious of all updates, and push to a separate branch for PR review"
        ).default(false)

        val testMode by commandParser.option(
            ArgType.Boolean,
            shortName = "t",
            description = "Whether to be in test mode, and push to the maintainer-test branch"
        ).default(false)


        commandParser.parse(args)
        val config = getOrCreateConfig(koin.get(), configLocation)

        // val discordBot = koin.get<Kord> { parametersOf(config.discordBotToken) }
        // discordBot.createGuildChatInputCommand(
        //     Snowflake(config.discordServer),
        //     "force-stop",
        //     "Immediately terminate the-mod-index-maintainer"
        // )
        // discordBot.login()
        while (true) {

            val updateSender = koin.get<UpdateSender> {
                parametersOf(
                    config.gitHubRepoName,
                    config.gitHubRepoOwner,
                    if (testMode) "maintainer-test" else "update",
                    config.gitHubAppId,
                    config.gitHubPrivateKeyPath,
                    sus
                )
            }
            val manifestReviewer =
                koin.get<ManifestReviewer> {
                    parametersOf(
                        "https://raw.githubusercontent.com/${config.gitHubRepoOwner}/${config.gitHubRepoName}/",
                        CURSEFORGE_API_KEY,
                        updateSender.gitHubInstallationToken
                    )
                }

            val updateExistingManifests =
                async {// This can take some time. Let's async await this, so that other things can get done.
                    logger.debug { "Starting the update of existing manifests" }
                    val existingManifests =
                        manifestReviewer.reviewExistingManifests(manifestReviewer.downloadOriginalManifests())
                    val manualReviewNeeded = updateSender.sendManifestUpdate(existingManifests)
                    manualReviewNeeded.collect() // TODO Send this info to Discord
                }

            updateExistingManifests.await()

            // TODO delay in hours, and repeat instead of exiting.
            exitProcess(0)
        }
    }
}

//     val apiDownloader = appComponent.indexApiDownloader
//     val index = apiDownloader.downloadIndexJson() ?: throw IOException("Could not download manifest index")
// /*
//         val manifestDownloadSemaphore = Semaphore(COROUTINES_PER_TASK)
//         val existingManifestIdentifiers = index.identifiers.map { it.substringBeforeLast(":") }
//         val existingManifestRequests = existingManifestIdentifiers.distinct().map {
//             async {
//                 manifestDownloadSemaphore.withPermit {
//                     apiDownloader.downloadManifestJson(it) ?: throw IOException("Could not download manifest $it")
//                 }
//             }
//         }
//
//         val modrinthApiCall = gitHubComponent.modrinthApiCall
//         val modrinthSearchChannel =
//             produceModrinthSearchResults(modrinthApiCall, doStarterSearch(modrinthApiCall).first())
//
//         val existingManifests = existingManifestRequests.awaitAll()
//         /*
//         Now that we have all existing manifests, we can start cross-referencing with modrinth & curse results to see what's missing
//         Discard Modrinth and Curse ids that are already in the existing manifests.
//         Those just need updating, and should be done separately (no need to repair Modrinth and Curse ids)
//          */
//         val newModrinthProjects = findNewModrinthProjects(existingManifests, modrinthSearchChannel)
//             .buffer(COROUTINES_PER_TASK)
//
//         //TODO Collect and create complete clause. Perhaps don't use a flow if that's more advantageous?
// */
//
// }
//
// // TODO timer thread that allocates when to do create manifests
// // TODO At pre-planned time of day, create new jobs for the day
// // TODO Then, create and push. We are likely be limited by gh rate limits. Maybe use BOTH the rest api and graphql api for more calls?
// // TODO Start off with retrieving all current manifests, modrinth projects, and CF mods. Find out what manifests need updating, and what manifests need creating
//
// fun findNewModrinthProjects(
//     existingManifests: List<ManifestJson>,
//     modrinthSearchChannel: ReceiveChannel<ModrinthResponse.SearchResponse.SearchHit>,
// ) = flow {
//     val existingIds = existingManifests.map { it.modrinthId }
//
//     for (modrinthProject in modrinthSearchChannel) {
//         if (modrinthProject.id !in existingIds) emit(modrinthProject)
//     }
// }
//
// fun doStarterSearch(modrinthApiCall: ModrinthApiCall) = flow {
//     val searchResponse = modrinthApiCall.search(limit = Int.MAX_VALUE).execute()
//
//     val starterSearch = searchResponse.body() ?: searchResponse.headers().get("x-ratelimit-remaining")?.let {
//         if (it.toInt() == 0) {
//             delay(searchResponse.headers().get("x-ratelimit-reset")!!.toLong())
//             modrinthApiCall.search(limit = Int.MAX_VALUE).execute().body()
//         } else throw IOException("Could not search modrinth")
//     } ?: throw IOException("Could not search modrinth")
//
//     emit(starterSearch)
// }
//
// // We require starter search to be done outside this function, as we need it to designate the buffer capacity.
// @ExperimentalCoroutinesApi
// fun CoroutineScope.produceModrinthSearchResults(
//     modrinthApiCall: ModrinthApiCall, starterSearch: ModrinthResponse.SearchResponse,
// ) = produce(capacity = starterSearch.limit * COROUTINES_PER_TASK) {
//     val totalNumOfProjects = starterSearch.totalHits
//     val maxSearchSize = starterSearch.limit
//     val nextToRequest = AtomicInteger(starterSearch.limit)
//     val modrinthRateLimitedSeconds = AtomicLong(0L)
//
//     launch { starterSearch.hits.forEach { send(it) } } // We already have the first batch of results, no need to make another call for it.
//
//     val searchResults = (1..COROUTINES_PER_TASK).map {
//
//         async {
//             while (nextToRequest.get() < totalNumOfProjects) {
//
//                 // Wait if rate limited. Else, 0L * 1000L == 0L delay
//                 delay(modrinthRateLimitedSeconds.get() * 1000L)
//
//                 val searchResponse = modrinthApiCall.search(
//                     offset = nextToRequest.get(), limit = maxSearchSize
//                 ).awaitResponse()
//
//                 searchResponse.body()?.hits?.let { hits ->
//                     nextToRequest.addAndGet(hits.size)
//                     hits.forEach { send(it) }
//                 }
//                 // Check if fail is due to rate limit
//                     ?: searchResponse.headers().get("x-ratelimit-remaining")?.let {
//                         if (it.toInt() == 0) {
//                             modrinthRateLimitedSeconds.set(
//                                 searchResponse.headers().get("x-ratelimit-reset")!!.toLong()
//                             )
//                         }
//
//                         while (modrinthRateLimitedSeconds.get() > 0) {
//                             delay(1000)
//                             modrinthRateLimitedSeconds.decrementAndGet()
//                         }
//                     }
//
//                     // Nope, fail not due to rate limit. Throw exception as we don't know what happened
//                     ?: throw IOException("Could not search modrinth")
//             }
//         }
//
//     }
//
//     searchResults.awaitAll() // Blocks, and waits for all given coroutines to finish (by doing all searches)
//     close()
// }
