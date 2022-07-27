package com.github.reviversmc.themodindex.creator.maintainer

import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.GHBranch
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.githubGraphqlModule
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.system.exitProcess

const val COROUTINES_PER_TASK = 5 // Arbitrary number of concurrent downloads. Change if better number is found.
const val FLOW_BUFFER = 5
const val INDEX_MAJOR = 4

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

fun main(args: Array<String>) = runBlocking {

    val koin = startKoin {
        modules(
            appModule, creatorModule, discordBotModule, githubGraphqlModule, manifestReviewModule, updateSenderModule
        )
    }.koin

    val commandParser = koin.get<ArgParser>()

    val configLocation by commandParser.option(
        ArgType.String, shortName = "c", description = "The location of the config file"
    ).default("the-mod-index-maintainer/config.json")

    val cooldownInHours by commandParser.option(
        ArgType.Int, shortName = "d", description = "How long to delay between updates"
    ).default(12)

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

    logger.debug { "Config location set to $configLocation" }
    logger.debug { "Cooldown set to $cooldownInHours hours" }
    logger.debug { "Sus mode: $sus" }
    logger.debug { "Test mode: $testMode" }

    val config = getOrCreateConfig(koin.get(), configLocation)

    val manifestRepo =
        "https://raw.githubusercontent.com/${config.gitHubRepoOwner}/${config.gitHubRepoName}/${if (testMode) "maintainer-test" else "v$INDEX_MAJOR"}/mods/"

    val updateSender by koin.inject<UpdateSender> {
        parametersOf(
            manifestRepo,
            config.gitHubRepoOwner,
            config.gitHubRepoName,
            if (testMode) "maintainer-test" else "update",
            config.gitHubAppId,
            config.gitHubPrivateKeyPath,
            sus
        )
    }


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

            val updateExistingManifests =
                launch {// This can take some time. Let's push this into a separate coroutine, and do other things as well
                    logger.debug { "Starting the update of existing manifests" }
                    val existingManifestReviewer = koin.get<ExistingManifestReviewer> {
                        parametersOf(
                            manifestRepo, config.curseForgeApiKey, updateSender.gitHubInstallationToken, testMode
                        )
                    }
                    val existingManifests = existingManifestReviewer.reviewManifests()
                    val manualReviewNeeded = updateSender.sendManifestUpdate(existingManifests)
                    manualReviewNeeded.collect { maintainerBot.sendConflict(it) }
                    logger.info { "Updated existing manifests" }
                }

            val createNewManifests = launch {
                logger.debug { "Starting the creation of new manifests" }
                val curseForgeManifestReviewer = koin.get<NewManifestReviewer>(named("curseforge")) {
                    parametersOf(
                        manifestRepo, config.curseForgeApiKey, updateSender.gitHubInstallationToken, testMode
                    )
                }

                val modrinthManifestReviewer = koin.get<NewManifestReviewer>(named("modrinth")) {
                    parametersOf(
                        manifestRepo, config.curseForgeApiKey, updateSender.gitHubInstallationToken, testMode
                    )
                }

                val newManifests = mutableMapOf<String, ManifestWithCreationStatus>()

                // Iter through modrinth first cause it is smaller (probably?)
                modrinthManifestReviewer.reviewManifests().buffer(FLOW_BUFFER)
                    .collect { newManifests[it.originalManifest.genericIdentifier] = it }
                curseForgeManifestReviewer.reviewManifests().buffer(FLOW_BUFFER).collect {
                    if (it.originalManifest.genericIdentifier !in newManifests) {
                        newManifests[it.originalManifest.genericIdentifier] = it
                    } else {
                        maintainerBot.sendConflict(newManifests[it.originalManifest.genericIdentifier]!!)
                        maintainerBot.sendConflict(it)
                    }
                }

                updateExistingManifests.join() // Wait for the update of existing manifests to finish, as we don't want to send a conflict.

                val manualReviewNeeded = updateSender.sendManifestUpdate(newManifests.values.asFlow())
                manualReviewNeeded.collect { maintainerBot.sendConflict(it) }
                logger.info { "Created new manifests" }
            }

            updateExistingManifests.join()
            createNewManifests.join()


            val ghGraphQLBranch = koin.get<GHBranch> {
                parametersOf(
                    updateSender.gitHubInstallationToken, config.gitHubRepoOwner, config.gitHubRepoName
                )
            }

            // If in test mode, we'll push to the maintainer-test branch. Don't PR or direct merge.
            if (!testMode) {
                if (!sus) {
                    ghGraphQLBranch.mergeBranchWithoutPR(
                        "v$INDEX_MAJOR", "update", "Automated manifest update: UTC ${ZonedDateTime.now(ZoneOffset.UTC)}"
                    )
                    logger.info { "Merged all update info into branch \"v$INDEX_MAJOR\"" }
                } else {
                    ghGraphQLBranch.createPullRequest(
                        "update",
                        "v$INDEX_MAJOR",
                        "Automated manifest update: UTC ${
                            ZonedDateTime.now(ZoneOffset.UTC).toString().replace(' ', '-').replace(':', '-')
                        }",
                        "Manual merger was requested when the maintainer was started. Please merge this PR manually should it meet standards."
                    )

                    logger.info { "Pushed manifest updates to branch \"update\". Manual PR merger required, as requested by startup flags." }
                }
            }

            logger.info { "Finished operation loop $operationLoopNum" }

            /*
            Notably, we do NOT wait for manifests sent for manual review to be reviewed before we move on to the next operation loop.
            The review process could take a long time if no one reviews it,
            and we don't want to stop regular updates for the few manifests that are not reviewed.

            Then, depending on settings, either quit or sleep for a while.
            */

            isCurrentlyUpdating = false
            if (shouldContinueUpdating) {
                logger.info { "Sleeping for $cooldownInHours hours" }
                delay(1000L * 60 * 60 * cooldownInHours)
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

