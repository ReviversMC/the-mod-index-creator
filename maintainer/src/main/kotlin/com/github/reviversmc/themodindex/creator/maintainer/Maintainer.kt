package com.github.reviversmc.themodindex.creator.maintainer

import com.apollographql.apollo3.ApolloClient
import com.github.reviversmc.themodindex.api.data.IndexJson
import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.GHBranch
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.githubMaintainerModule
import com.github.reviversmc.themodindex.creator.maintainer.data.AppConfig
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.maintainer.github.UpdateSender
import com.github.reviversmc.themodindex.creator.maintainer.github.updateSenderModule
import com.github.reviversmc.themodindex.creator.maintainer.reviewer.ExistingManifestReviewer
import com.github.reviversmc.themodindex.creator.maintainer.reviewer.NewManifestReviewer
import com.github.reviversmc.themodindex.creator.maintainer.reviewer.manifestReviewModule
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.multiple
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.concurrent.timer
import kotlin.system.exitProcess

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

    print("Please enter you CurseForge Api Key: \n> ")
    val apiKey = readlnOrNull() ?: throw IOException("No API key provided")

    print("Please indicate the ID of the GitHub app to use: \n> ")
    val appId = readlnOrNull() ?: throw IOException("No GitHub App ID provided.")

    print("Please indicate the path of the GitHub app's private key: \n> ")
    val privateKey = readlnOrNull() ?: throw IOException("No private key provided.")

    print("Please indicate the GitHub owner of the manifest repository: \n> ")
    val owner = readlnOrNull() ?: throw IOException("No owner provided.")

    print("Please indicate name of the GitHub manifest repository: \n> ")
    val repoName = readlnOrNull() ?: throw IOException("No repository provided.")

    return AppConfig(apiKey, appId, privateKey, owner, repoName).also {
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
    PROD, TEST_SELECTED, TEST_SHORT
}

@Suppress("unused") // We want all available options
enum class OperationMode {
    CREATE {
        override fun maintainRegex() = null
    },
    MAINTAIN_ALL {
        override fun maintainRegex() = Regex("^[a-z0-9\\-_]+:[a-z0-9\\-_]+$")
    },
    MAINTAIN_FABRIC {
        override fun maintainRegex() = Regex("^fabric:[a-z0-9\\-_]+$")
    },
    MAINTAIN_FORGE {
        override fun maintainRegex() = Regex("^forge:[a-z0-9\\-_]+$")
    },
    MAINTAIN_QUILT {
        override fun maintainRegex() = Regex("^quilt:[a-z0-9\\-_]+$")
    },
    MAINTAIN_MISC {
        override fun maintainRegex() =
            Regex("^(?!.*\bfabric\b)(?!.*\bforge\b)(?!.*\bquilt\b)[a-z0-9\\-_]+:[a-z0-9\\-_]+$")
    };

    abstract fun maintainRegex(): Regex?
}

fun main(args: Array<String>) = runBlocking {

    val koin = startKoin {
        modules(
            appModule, creatorModule, githubMaintainerModule, manifestReviewModule, updateSenderModule
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
    ).default(RunMode.TEST_SELECTED)

    val operationMode by commandParser.option(
        ArgType.Choice<OperationMode>(), shortName = "o", description = "What kind of operations to run"
    ).multiple().default(listOf(OperationMode.CREATE, OperationMode.MAINTAIN_ALL))

    commandParser.parse(args)

    logger.debug { "Config location set to $configLocation" }
    logger.debug { "Cooldown set to $cooldownInHours hours" }
    logger.debug { "Run mode: $runMode" }
    logger.debug { "Operation mode: $operationMode" }

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

    try {
        while (shouldContinueUpdating) {
            ++operationLoopNum
            isCurrentlyUpdating = true
            logger.info { "Starting operation loop $operationLoopNum" }

            val manifestsToCommit = mutableListOf<ManifestWithCreationStatus>()
            val manifestsToCommitMutex = Mutex()

            suspend fun pushChanges() {
                manifestsToCommitMutex.withLock {
                    val manualReviewNeeded = updateSender.sendManifestUpdate(manifestsToCommit)
                    manualReviewNeeded.buffer(FLOW_BUFFER).collect { updateSender.sendConflict(it) }
                    manifestsToCommit.clear()
                }
            }

            val regularUpdates = timer("", true, 60L * 60L * 1000L, 60L * 60L * 1000L) {
                launch {
                    pushChanges()
                }
            }

            suspend fun submitGeneratedManifests(manifestFlow: Flow<ManifestWithCreationStatus>) {
                manifestFlow.buffer(FLOW_BUFFER).collect {
                    manifestsToCommitMutex.withLock { manifestsToCommit.add(it) }
                }
            }

            // Download entire repo as .tar.gz (don't use zip cause every file is compressed!) and read off that instead of making individual requests
            val existingManifests = mutableListOf<ManifestJson>().apply {
                val tarGZ = ghGraphQLBranch.downloadBranchTarGZ(workingBranch, koin.get())
                GZIPInputStream(tarGZ).use { tarGZInput ->
                    TarArchiveInputStream(tarGZInput).use { tarInput ->
                        generateSequence { tarInput.nextTarEntry }.also { entries ->

                            // Stored in map so that we can iterate/find from this as many times as we want (sequences are not reusable)
                            val entryWithStream =
                                entries.associate { it.name to if (it.isFile) tarInput.readBytes() else null }

                            val baseName =
                                entryWithStream.keys.first { it.substringAfter("/") == "" }.substringBefore("/")
                            logger.debug { "Base name for manifests is: $baseName" }

                            val indexJson = entryWithStream["$baseName/mods/index.json"]?.let {
                                koin.get<Json>().decodeFromString<IndexJson>(it.decodeToString())
                            } ?: throw IOException("Could not find manifest index from $manifestRepo")
                            logger.debug { "Retrieved manifest index of repository $manifestRepo" }

                            indexJson.identifiers.map { it.substringBeforeLast(":") }.distinct()
                                .forEach { genericIdentifier ->
                                    val manifestJson =
                                        entryWithStream["$baseName/mods/${genericIdentifier.substringBefore(":")}/${
                                            genericIdentifier.substringAfter(
                                                ":"
                                            )
                                        }.json"]?.let {
                                            koin.get<Json>().decodeFromString<ManifestJson>(
                                                it.decodeToString()
                                            )
                                        }
                                            ?: throw IOException("Could not find manifest for $genericIdentifier from $manifestRepo")
                                    logger.debug { "Retrieved manifest for $genericIdentifier from $manifestRepo" }
                                    add(manifestJson)
                                }
                        }
                    }
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
                            createGitHubClient,
                            existingManifests,
                            runMode,
                            operationMode
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
                        createGitHubClient,
                        existingManifests,
                        runMode,
                        operationMode
                    )
                }

                val modrinthManifestReviewer = koin.get<NewManifestReviewer>(named("modrinth")) {
                    parametersOf(
                        manifestRepo,
                        config.curseForgeApiKey,
                        createGitHubClient,
                        existingManifests,
                        runMode,
                        operationMode
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
            pushChanges()

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
                exitProcess(0)
            }
        }
    } catch (ex: Exception) {/*
        Our intent here isn't to do a global catch-all to prevent the maintainer from crashing.
        If the exception is not handled by this point, we probably want the maintainer to crash, instead of pretending that everything is fine.
        Thus, the point of this try catch is to log the exception to Discord, and clean up the maintainer.
         */

        logger.error(ex) { "Exception in maintainer loop $operationLoopNum" }
        exitProcess(1)
    }
}

