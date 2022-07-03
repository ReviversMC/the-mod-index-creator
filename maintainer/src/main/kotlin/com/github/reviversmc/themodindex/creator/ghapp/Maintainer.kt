package com.github.reviversmc.themodindex.creator.ghapp

import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.ghapp.apicalls.GHBranch
import com.github.reviversmc.themodindex.creator.ghapp.apicalls.githubGraphqlModule
import com.github.reviversmc.themodindex.creator.ghapp.data.AppConfig
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.ghapp.data.ReviewStatus
import com.github.reviversmc.themodindex.creator.ghapp.github.UpdateSender
import com.github.reviversmc.themodindex.creator.ghapp.github.updateSenderModule
import com.github.reviversmc.themodindex.creator.ghapp.reviewer.ExistingManifestReviewer
import com.github.reviversmc.themodindex.creator.ghapp.reviewer.NewManifestReviewer
import com.github.reviversmc.themodindex.creator.ghapp.reviewer.manifestReviewModule
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.UpdateStatus
import dev.kord.rest.builder.interaction.int
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import okhttp3.internal.wait
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.system.exitProcess

const val CURSEFORGE_API_KEY = "\$2a\$10\$VM7TVUzpLUKp1MwvLOyG3uTMl1gVen39dL8uZsLd2tCX00D1Sw7V2"
const val COROUTINES_PER_TASK = 5 // Arbitrary number of concurrent downloads. Change if better number is found.
const val INDEX_MAJOR = 4

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

private suspend fun startDiscordBot(koin: Koin, config: AppConfig) {
    val discordBot = koin.get<Kord> { parametersOf(config.discordBotToken) }

    // discordBot.createGuildChatInputCommand(
    //     Snowflake(config.discordServer),
    //     "force-stop",
    //     "Immediately terminate the-mod-index-maintainer"
    // )
    //
    // discordBot.on<GuildChatInputCommandInteractionCreateEvent> {
    //     val response = interaction.deferPublicResponse()
    //     if (interaction.command.rootName == "force-stop") {
    //         response.getFollowupMessageOrNull(Snowflake("Stopping the-mod-index-maintainer..."))
    //         discordBot.shutdown()
    //         exitProcess(0)
    //     }
    // }

    discordBot.createGuildChatInputCommand(
        Snowflake(894817834046201916), "sum", "A slash command that sums two numbers"
    ) {
        int("first_number", "The first operand") {
            required = true
        }
        int("second_number", "The second operand") {
            required = true
        }
    }
    discordBot.on<GuildChatInputCommandInteractionCreateEvent> {
        val response = interaction.deferPublicMessage()
        val command = interaction.command
        val first = command.integers["first_number"]!! // it's required so it's never null
        val second = command.integers["second_number"]!!
        response.edit { content = "$first + $second = ${first + second}" }
    }


    discordBot.login { // Reminder: Login is suspending!
        intents = Intents(Intent.Guilds, Intent.GuildMessages)
    }
}

@OptIn(DelicateCoroutinesApi::class)
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
        val config = getOrCreateConfig(koin.get(), configLocation)

        // launch { startDiscordBot(koin, config) }
        // delay(1000 * 60 * 60)

        val updateSender by koin.inject<UpdateSender> {
            parametersOf(
                config.gitHubRepoName,
                config.gitHubRepoOwner,
                if (testMode) "maintainer-test" else "update",
                config.gitHubAppId,
                config.gitHubPrivateKeyPath,
                sus
            )
        }

        val manifestRepo = "https://raw.githubusercontent.com/${config.gitHubRepoOwner}/${config.gitHubRepoName}/"


        // All variables that need to be refreshed (i.e. that use a gh api key) should be in the while loop.
        var operationLoopNum = 0

        @Suppress("KotlinConstantConditions")
        while (++operationLoopNum != 0) {

            logger.info { "Starting operation loop $operationLoopNum" }

            val updateExistingManifests =
                launch {// This can take some time. Let's push this into a separate coroutine, and do other things as well
                    logger.debug { "Starting the update of existing manifests" }
                    val existingManifestReviewer = koin.get<ExistingManifestReviewer> {
                        parametersOf(
                            manifestRepo, CURSEFORGE_API_KEY, updateSender.gitHubInstallationToken
                        )
                    }
                    val existingManifests = existingManifestReviewer.reviewManifests()
                    val manualReviewNeeded = updateSender.sendManifestUpdate(existingManifests)
                    manualReviewNeeded.collect {} // TODO Send this info to Discord
                }

            val createNewManifests = launch {
                logger.debug { "Starting the creation of new manifests" }
                val curseForgeManifestReviewer = koin.get<NewManifestReviewer>(named("curseforge")) {
                    parametersOf(
                        manifestRepo, CURSEFORGE_API_KEY, updateSender.gitHubInstallationToken
                    )
                }

                val modrinthManifestReviewer = koin.get<NewManifestReviewer>(named("modrinth")) {
                    parametersOf(
                        manifestRepo, CURSEFORGE_API_KEY, updateSender.gitHubInstallationToken
                    )
                }

                val newManifests = mutableMapOf<String, ManifestWithCreationStatus>()

                val newManifestContext = newSingleThreadContext("new-manifest-context")
                withContext(newManifestContext) {

                    suspend fun collectNewManifests(newManifestReviewer: NewManifestReviewer) =
                        newManifestReviewer.reviewManifests().collect {
                            if (it.originalManifest.genericIdentifier !in newManifests) {
                                newManifests[it.originalManifest.genericIdentifier] = it
                            } else {

                                // Attempt to do a merger of the two manifests.
                                val generatedManifest = it
                                val conflictingManifest = newManifests[it.originalManifest.genericIdentifier]!!

                                if ((generatedManifest.latestManifest!!.curseForgeId != null && conflictingManifest.latestManifest!!.curseForgeId != null) ||
                                    (generatedManifest.latestManifest.modrinthId != null && conflictingManifest.latestManifest!!.modrinthId != null) ||
                                    (generatedManifest.latestManifest.license != conflictingManifest.latestManifest!!.license) ||
                                    (generatedManifest.latestManifest.fancyName != generatedManifest.latestManifest.fancyName)) {
                                    newManifests[it.originalManifest.genericIdentifier] = ManifestWithCreationStatus(
                                        ReviewStatus.CREATION_CONFLICT,
                                        it.latestManifest,
                                        newManifests[it.originalManifest.genericIdentifier]!!.originalManifest // Not null as we just confirmed that it's in the map
                                    )
                                } else {
                                    // TODO in the future, replace this with automatic merging. At this time, we still need real world data to determine strictness.
                                    newManifests[it.originalManifest.genericIdentifier] = ManifestWithCreationStatus(
                                        ReviewStatus.CREATION_CONFLICT,
                                        it.latestManifest,
                                        newManifests[it.originalManifest.genericIdentifier]!!.originalManifest // Not null as we just confirmed that it's in the map
                                    )
                                }



                            }
                        }

                    val curseManifests = launch { collectNewManifests(curseForgeManifestReviewer) }
                    val modrinthManifests = launch { collectNewManifests(modrinthManifestReviewer) }
                    curseManifests.wait()
                    modrinthManifests.wait()
                }

                updateExistingManifests.join() // Wait for the update of existing manifests to finish, as we don't want to send a conflict.

                val manualReviewNeeded = updateSender.sendManifestUpdate(newManifests.values.asFlow())
                manualReviewNeeded.collect {} // TODO Send this info to Discord
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
                        "v$INDEX_MAJOR",
                        "update",
                        "Automated manifest update: UTC ${ZonedDateTime.now(ZoneOffset.UTC)}"
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
            exitProcess(0)
            // Replace the exitProcess with a delay, and then restart the loop.
            // delay(1000 * 60 * 60 * cooldownInHours)
        }
    }
}
