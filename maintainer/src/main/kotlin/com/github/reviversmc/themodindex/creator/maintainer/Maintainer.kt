package com.github.reviversmc.themodindex.creator.maintainer

import com.apollographql.apollo3.ApolloClient
import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.GHBranch
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.githubMaintainerModule
import com.github.reviversmc.themodindex.creator.maintainer.data.AppConfig
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.maintainer.discordbot.MaintainerBot
import com.github.reviversmc.themodindex.creator.maintainer.discordbot.discordBotModule
import com.github.reviversmc.themodindex.creator.maintainer.github.UpdateSender
import com.github.reviversmc.themodindex.creator.maintainer.github.updateSenderModule
import com.github.reviversmc.themodindex.creator.maintainer.reviewer.ExistingManifestReviewer
import com.github.reviversmc.themodindex.creator.maintainer.reviewer.NewManifestReviewer
import com.github.reviversmc.themodindex.creator.maintainer.reviewer.manifestReviewModule
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.channel.TextChannel
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import java.io.File
import java.io.IOException
import kotlin.concurrent.timer
import kotlin.system.exitProcess

const val COROUTINES_PER_TASK = 5 // Arbitrary number of concurrent downloads. Change if better number is found.
const val FLOW_BUFFER = 5
const val INDEX_MAJOR = 5

private val logger = KotlinLogging.logger {}

var shouldContinueUpdating = true
var isCurrentlyUpdating = false

private fun getOrCreateConfig(json: Json, location: String, exitIfCreate: Boolean = false): AppConfig {
    val configFile = File(location)
    if (configFile.exists() && configFile.isFile) {
        logger.info { "Found config file at $location" }
        return json.decodeFromString(configFile.readText())
    }

    logger.info { "No config file found at $location. Creating one now." }

    print("Please enter you CurseForge Api Key: \n > ")
    val apiKey = readlnOrNull() ?: throw IOException("No API key provided")

    print("Please indicate your Discord bot token: \n > ")
    val botToken = readlnOrNull() ?: throw IOException("No token provided.")

    print("Please indicate the ID of the Discord server to post to: \n > ")
    val serverId = readlnOrNull()?.toLongOrNull() ?: throw IOException("Invalid server ID.")

    print("Please indicate the ID of the Discord channel to post to. This should be a text channel, and should not be a thread: \n > ")
    val channelId = readlnOrNull()?.toLongOrNull() ?: throw IOException("Invalid channel ID.")

    print("Please indicate the ID of the GitHub app to use: \n > ")
    val appId = readlnOrNull() ?: throw IOException("No GitHub App ID provided.")

    print("Please indicate the path of the GitHub app's private key: \n > ")
    val privateKey = readlnOrNull() ?: throw IOException("No private key provided.")

    print("Please indicate the GitHub owner of the manifest repository: \n > ")
    val owner = readlnOrNull() ?: throw IOException("No owner provided.")

    print("Please indicate name of the GitHub manifest repository: \n > ")
    val repoName = readlnOrNull() ?: throw IOException("No repository provided.")

    return AppConfig(apiKey, botToken, serverId, channelId, appId, privateKey, owner, repoName).also {
        configFile.parentFile.mkdirs()
        configFile.writeText(json.encodeToString(it))
        logger.info { "Config file created at ${configFile.absolutePath}." }

        if (exitIfCreate) {
            println("App exiting. Please relaunch the app to use the new config.")
            exitProcess(0)
        }
    }
}

enum class RunMode {
    PROD,
    TEST_SHORT,
    TEST_FULL,
}

fun main(args: Array<String>) = runBlocking {

    val koin = startKoin {
        modules(
            appModule, creatorModule, discordBotModule, githubMaintainerModule, manifestReviewModule, updateSenderModule
        )
    }.koin

    val commandParser = koin.get<ArgParser>()

    val configLocation by commandParser.option(
        ArgType.String, shortName = "c", description = "The location of the config file"
    ).default("the-mod-index-maintainer/config.json")

    val cooldownInHours by commandParser.option(
        ArgType.Int, shortName = "d", description = "How long to delay between updates"
    ).default(12)

    val runMode by commandParser.option(
        ArgType.Choice<RunMode>(),
        shortName = "r",
        description = "Whether to run in prod mode, or push to separate branches for testing"
    ).default(RunMode.TEST_FULL)

    commandParser.parse(args)

    logger.debug { "Config location set to $configLocation" }
    logger.debug { "Cooldown set to $cooldownInHours hours" }
    logger.debug { "Run mode: ${runMode.name}" }

    val config = getOrCreateConfig(koin.get(), configLocation)

    val manifestRepo =
        "https://raw.githubusercontent.com/${config.gitHubRepoOwner}/${config.gitHubRepoName}/${if (runMode == RunMode.PROD) "v$INDEX_MAJOR" else "maintainer-test"}/mods/"
    val workingBranch = if (runMode == RunMode.PROD) "v$INDEX_MAJOR" else "maintainer-test"

    val updateSender by koin.inject<UpdateSender> {
        parametersOf(
            manifestRepo,
            config.gitHubRepoOwner,
            config.gitHubRepoName,
            workingBranch,
            config.gitHubAppId,
            config.gitHubPrivateKeyPath
        )
    }

    val createGitHubClient = {
        koin.get<ApolloClient> { parametersOf(updateSender.gitHubInstallationToken) }
    }

    val ghGraphQLBranch = koin.get<GHBranch> {
        parametersOf(
            createGitHubClient, config.gitHubRepoOwner, config.gitHubRepoName
        )
    }

    if (!ghGraphQLBranch.doesRefExist(workingBranch)) ghGraphQLBranch.createRef("v$INDEX_MAJOR", workingBranch)


    // All variables that need to be refreshed (i.e. that use a gh api key) should be in the while loop.
    var operationLoopNum = 0
    val maintainerBot = withContext(Dispatchers.Default) {
        val kord = koin.get<Kord> { parametersOf(config.discordBotToken) }
        val parentChannel = kord.getChannelOf<TextChannel>(Snowflake(config.discordTextChannel))
        koin.get<MaintainerBot> {
            parametersOf(
                config.discordBotToken, Snowflake(config.discordServer), parentChannel
            )
        }
    }

    launch { maintainerBot.start() } // This suspends till the bot is shutdown. Move it to a separate coroutine so that we can still do stuff
    logger.info { "Started Discord Bot" }
    launch {
        for (resolvedConflict in maintainerBot.resolvedConflicts) updateSender.sendManifestUpdate(
            flowOf(
                resolvedConflict
            )
        )
    }

    try {
        while (shouldContinueUpdating) {
            ++operationLoopNum
            isCurrentlyUpdating = true
            logger.info { "Starting operation loop $operationLoopNum" }

            val manifestsToCommit = mutableListOf<ManifestWithCreationStatus>()
            val manifestsToCommitMutex = Mutex()

            suspend fun pushChanges(manifestToCommitFlow: Flow<ManifestWithCreationStatus>) {
                manifestsToCommitMutex.withLock {
                    val manualReviewNeeded = updateSender.sendManifestUpdate(manifestToCommitFlow)
                    manualReviewNeeded.buffer(FLOW_BUFFER).collect { maintainerBot.sendConflict(it) }
                    manifestsToCommit.clear()
                }
            }

            val regularUpdates = timer("", true, 60L * 60L * 1000L, 60L * 60L * 1000L) {
                launch {
                    pushChanges(manifestsToCommit.asFlow())
                }
            }

            suspend fun submitGeneratedManifests(manifestFlow: Flow<ManifestWithCreationStatus>) {
                manifestFlow.buffer(FLOW_BUFFER).collect {
                    manifestsToCommitMutex.withLock { manifestsToCommit.add(it) }
                }
            }

            val existingManifests = mutableListOf<ManifestJson>().apply {
                val apiDownloader = koin.get<ApiDownloader>(named("custom")) { parametersOf(manifestRepo) }
                    val existingGenericIdentifiers =
                        apiDownloader.downloadIndexJson()?.identifiers?.map { it.substringBeforeLast(":") }
                            ?: throw IOException("Could not download manifest index from ${apiDownloader.formattedBaseUrl}")
                    logger.debug { "Downloaded manifest index of repository ${apiDownloader.formattedBaseUrl}" }

                    existingGenericIdentifiers.distinct().forEach {
                        add(
                            apiDownloader.downloadManifestJson(it)
                                ?: throw IOException("Could not download manifest $it")
                        )
                        logger.debug { "Downloaded manifest $it" }
                    }
                }.toList()

            logger.info { "Found ${existingManifests.size} existing manifests" }


            val updateExistingManifests =
                launch {// This can take some time. Let's push this into a separate coroutine, and do other things as well
                    logger.debug { "Starting the update of existing manifests" }
                    val existingManifestReviewer = koin.get<ExistingManifestReviewer> {
                        parametersOf(
                            manifestRepo,
                            config.curseForgeApiKey,
                            existingManifests,
                            createGitHubClient,
                            runMode
                        )
                    }

                    submitGeneratedManifests(existingManifestReviewer.reviewManifests())
                    logger.info { "Updated existing manifests" }
                }

            val createNewManifests = launch {
                logger.debug { "Starting the creation of new manifests" }
                val curseForgeManifestReviewer = koin.get<NewManifestReviewer>(named("curseforge")) {
                    parametersOf(
                        manifestRepo,
                        config.curseForgeApiKey,
                        existingManifests,
                        createGitHubClient,
                        runMode
                    )
                }

                val modrinthManifestReviewer = koin.get<NewManifestReviewer>(named("modrinth")) {
                    parametersOf(
                        manifestRepo,
                        config.curseForgeApiKey,
                        existingManifests,
                        createGitHubClient,
                        runMode
                    )
                }


                val curseForgeCreation = launch {
                    submitGeneratedManifests(curseForgeManifestReviewer.reviewManifests())
                    logger.info { "Pushed all created CF manifests" }
                }
                val modrinthCreation = launch {
                    submitGeneratedManifests(modrinthManifestReviewer.reviewManifests())
                    logger.info { "Pushed all created Modrinth manifests" }
                }

                curseForgeCreation.join()
                modrinthCreation.join()
                logger.info { "Created new manifests" }
            }

            updateExistingManifests.join()
            createNewManifests.join()

            // Stop the scheduled task and push one last time for all changes to go through
            regularUpdates.cancel()
            pushChanges(manifestsToCommit.asFlow())

            /*
            By this point, most changes are pushed.
            However, we do NOT wait for manifests sent for manual review to be reviewed before we move on to the next operation loop.
            The review process could take a long time if no one reviews it,
            and we don't want to stop regular updates for the few manifests that are not reviewed.

            Then, depending on settings, either quit or sleep for a while.
            */

            isCurrentlyUpdating = false
            if (shouldContinueUpdating) {
                logger.info { "Sleeping for $cooldownInHours hours" }
                delay(cooldownInHours * 60L * 60L * 1000L)
            } else {
                maintainerBot.exit()
                exitProcess(0)
            }
        }
    } catch (ex: Exception) {
        /*
        Our intent here isn't to do a global catch-all to prevent the maintainer from crashing.
        If the exception is not handled by this point, we probably want the maintainer to crash, instead of pretending that everything is fine.
        Thus, the point of this try catch is to log the exception to Discord, and clean up the maintainer.
         */

        logger.error(ex) { "Exception in maintainer loop $operationLoopNum" }
        maintainerBot.exit(ex.message ?: "An unknown exception occurred!\n $ex", 1)
        exitProcess(1)
    }
}

