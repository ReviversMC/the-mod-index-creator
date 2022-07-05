package com.github.reviversmc.themodindex.creator.ghapp.discordbot

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.data.ManifestLinks
import com.github.reviversmc.themodindex.api.data.VersionFile
import com.github.reviversmc.themodindex.creator.ghapp.COROUTINES_PER_TASK
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.ghapp.data.ReviewStatus
import com.github.reviversmc.themodindex.creator.ghapp.isCurrentlyUpdating
import com.github.reviversmc.themodindex.creator.ghapp.shouldContinueUpdating
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.thread.TextChannelThread
import dev.kord.core.entity.interaction.ButtonInteraction
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.create.actionRow
import dev.kord.rest.builder.message.modify.actionRow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import kotlin.system.exitProcess

/**
 * Represents a related pair of a [Message] and a [ManifestWithCreationStatus], where the [Message] is sent to resolve the [ManifestWithCreationStatus]
 * @author ReviversMC
 * @since 1.0.0
 */
private typealias ConflictMessageWithInfo = Pair<Message, ManifestWithCreationStatus>

/**
 * Just another way to specify a [String], while hopefully providing a bit more clarity into what the string is meant to represent.
 * @author ReviversMC
 * @since 1.0.0
 */
private typealias GenericIdentifier = String

class ModIndexMaintainerBot(
    private val json: Json,
    private val kord: Kord,
    private val guildId: Snowflake,
    private val parentTextChannel: TextChannel,
) : MaintainerBot {

    override val resolvedConflicts = Channel<ManifestWithCreationStatus>(COROUTINES_PER_TASK * 2)

    private val logger = KotlinLogging.logger {}
    private val commandPermsRequired = Permissions(Permission.ModerateMembers)
    private var startupMessage: Message? = null

    private val conflictMessages = mutableMapOf<GenericIdentifier, ConflictMessageWithInfo>()

    private suspend fun createRequiredThreads() {
        val activeThreads = mutableListOf<TextChannelThread>()
        parentTextChannel.activeThreads.collect { activeThreads.add(it) }
        logger.debug { "Found active Discord threads ${activeThreads.map { it.name }}" }

        val wantedThreads = mutableListOf("maintainer-status", "maintainer-conflicts", "maintainer-resolved")

        val wantedThreadsIter = wantedThreads.iterator()
        while (wantedThreadsIter.hasNext()) {
            val wantedThread = wantedThreadsIter.next()
            if (!(activeThreads.none { it.name == wantedThread })) {
                wantedThreadsIter.remove()
                logger.debug { "Found thread $wantedThread" }
            }
        }

        if (wantedThreads.isEmpty()) return

        val archivedThreads = mutableListOf<TextChannelThread>()
        parentTextChannel.getPublicArchivedThreads().collect { archivedThreads.add(it) }

        wantedThreads.forEach { wantedThread ->
            // If thread is still not found, just create it
            archivedThreads.firstOrNull { it.name == wantedThread }?.createMessage("Chat unarchive!")?.apply {
                logger.debug { "Unarchived thread $wantedThread" }
                delay(1000L * 3)
                delete("Delete unarchive message")
            } ?: parentTextChannel.startPublicThread(wantedThread).also {
                logger.debug { "Created thread $wantedThread" }
            }
        }
    }

    private suspend fun executeExitCommands(
        command: String,
        deferred: DeferredPublicMessageInteractionResponseBehavior,
    ) = when (command) {
        "schedule-exit" -> {
            "Exit scheduled.".also {
                deferred.respond { content = it }.apply {
                    delay(1000L * 3)
                    delete()
                }
                logger.debug { it }
            }
            shouldContinueUpdating = false
            if (!isCurrentlyUpdating) exit()
            true
        }
        "force-exit" -> {
            "Forced exit!".also {
                deferred.respond { content = it }.apply {
                    delay(1000L * 3)
                    delete()
                }
                logger.debug { it }
            }
            exit()
            true
        }
        else -> {
            "Unknown command $command!".also {
                deferred.respond { content = it }.apply {
                    delay(1000L * 3)
                    delete()
                }
                logger.debug { it }
            }
            false
        }
    }


    private suspend fun registerCommands() {
        kord.createGuildChatInputCommand(
            guildId, "schedule-exit", "Get the maintainer to shut down at the next safe moment"
        ) {
            defaultMemberPermissions = commandPermsRequired
        }
        logger.debug { "Registered command \"schedule-exit\"" }

        kord.createGuildChatInputCommand(
            guildId, "force-exit", "Get the maintainer to shut down immediately, regardless if data will be lost"
        ) {
            defaultMemberPermissions = commandPermsRequired
        }
        logger.debug { "Registered command \"force-exit\"" }
    }

    override suspend fun exit(exitMessage: String, exitCode: Int) {
        resolvedConflicts.close()
        startupMessage?.edit {
            content = if (exitMessage.length < 2000) exitMessage else exitMessage.substring(0, 1997) + "..."
            components = mutableListOf() // Clear the action row
        }
        logger.debug { "Edited startup message" }

        conflictMessages.forEach { it.value.first.delete("Maintainer shutdown, references to conflicts deleted") }
        logger.debug { "Deleted all conflict messages" }
        kord.shutdown()
        exitProcess(1)
    }

    private suspend fun sendStartupMessage() {
        val maintainerStatusThread = parentTextChannel.activeThreads.firstOrNull {
            it.name == "maintainer-status"
        } ?: throw IllegalStateException("Maintainer status thread not found")
        logger.debug { "Obtained maintainer-status thread" }

        startupMessage =
            maintainerStatusThread.messages.firstOrNull { it.content == "The maintainer is now **online**" }?.also {
                logger.debug { "Maintainer status thread already has a startup message" }

            } ?: maintainerStatusThread.messages.firstOrNull {
                it.content == defaultShutdownMessage
            }?.edit {
                content = "The maintainer is now **online**"
                actionRow {
                    interactionButton(ButtonStyle.Primary, "schedule-exit") {
                        label = "Shutdown at next safe moment"
                    }
                    interactionButton(ButtonStyle.Danger, "force-exit") {
                        label = "Shutdown immediately"
                    }
                }

                // It could be possible that the last shutdown message was an error message. Thus, we should leave it for reference, and create a new message
            } ?: maintainerStatusThread.createMessage {
                content = "The maintainer is now **online**"
                actionRow {
                    interactionButton(ButtonStyle.Primary, "schedule-exit") {
                        label = "Shutdown at next safe moment"
                    }
                    interactionButton(ButtonStyle.Danger, "force-exit") {
                        label = "Shutdown immediately"
                    }
                }
            }
        logger.debug { "Created startup message" }


    }

    private fun registerEvents() {
        kord.on<GuildChatInputCommandInteractionCreateEvent> {
            val deferred = interaction.deferPublicResponse()
            for (permission in commandPermsRequired.values) {
                if (permission !in interaction.permissions) {
                    deferred.respond { content = "You do not have permission to use this command!" }.apply {
                        delay(1000L * 3)
                        delete()
                    }
                    return@on
                }
            }

            // Continue updating and actually working are top level
            executeExitCommands(interaction.invokedCommandName, deferred)
        }

        kord.on<ButtonInteractionCreateEvent> {
            val deferred = interaction.deferPublicResponse()
            for (permission in commandPermsRequired.values) {
                if (interaction.data.permissions.value?.values?.contains(permission) != true) {
                    deferred.respond { content = "You do not have permission to use this command!" }.apply {
                        delay(1000L * 3)
                        delete()
                    }
                    return@on
                }
            }
            if (interaction.componentId == "schedule-exit" || interaction.componentId == "force-exit") {
                executeExitCommands(interaction.componentId, deferred)
            } else sortConflictButtonClicks(interaction, deferred)
        }
    }

    override suspend fun start() {
        createRequiredThreads()
        registerCommands()
        registerEvents()
        sendStartupMessage()
        logger.debug { "Setup complete for Discord Bot. Logging in..." }
        kord.login {
            @OptIn(PrivilegedIntent::class)
            intents += Intent.MessageContent
        }
    }

    override suspend fun sendConflict(manifestWithCreationStatus: ManifestWithCreationStatus) {
        val maintainerConflictsThread = parentTextChannel.activeThreads.firstOrNull {
            it.name == "maintainer-conflicts"
        } ?: throw IllegalStateException("Maintainer conflicts thread not found")

        if (manifestWithCreationStatus.reviewStatus != ReviewStatus.CREATION_CONFLICT && manifestWithCreationStatus.reviewStatus != ReviewStatus.UPDATE_CONFLICT) {
            logger.warn { "Tried to send conflict to Discord for manifest with status ${manifestWithCreationStatus.reviewStatus}" }
            return
        }

        val genericIdentifier = manifestWithCreationStatus.originalManifest.genericIdentifier
        val originalManifestSplit = json.encodeToString(manifestWithCreationStatus.originalManifest).split("\n")
        val latestManifestSplit = json.encodeToString(manifestWithCreationStatus.latestManifest).split("\n")
        val diff = DiffUtils.diff(originalManifestSplit, latestManifestSplit)
        val unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
            "mods/${genericIdentifier.replaceFirst(':', '/')}.json",
            "mods/${genericIdentifier.replaceFirst(':', '/')}.json",
            originalManifestSplit,
            diff,
            originalManifestSplit.size.coerceAtLeast(latestManifestSplit.size) // Show the full files, not just the diff
        )

        val messageContent =
            """${if (manifestWithCreationStatus.reviewStatus == ReviewStatus.CREATION_CONFLICT) "Creation" else "Update"} conflict for genericIdentifier
            ```diff
            ${unifiedDiff.joinToString("\n")}
            ```
        """.trimIndent()

        val conflictMessage = maintainerConflictsThread.createMessage {
            content = messageContent

            actionRow {
                interactionButton(
                    ButtonStyle.Primary,
                    "accept-original:genericIdentifier"
                ) {
                    label = "Accept original"
                }

                interactionButton(
                    ButtonStyle.Primary,
                    "accept-latest:genericIdentifier"
                ) {
                    label = "Accept newer"
                }

                interactionButton(
                    ButtonStyle.Primary, "merge:genericIdentifier"
                ) {
                    label = "Merge manifests"
                }

                interactionButton(
                    ButtonStyle.Secondary,
                    "dismiss:genericIdentifier"
                ) {
                    label = "Dismiss manifests"
                }

            }
        }
        logger.debug { "Created diff message" }

        // Delete old message if it exists
        conflictMessages[genericIdentifier]?.first?.delete("There is a newer conflict available for ")

        conflictMessages[genericIdentifier] = Pair(conflictMessage, manifestWithCreationStatus)
        logger.debug { "Stored conflict message" }
    }

    private suspend fun sortConflictButtonClicks(
        interaction: ButtonInteraction,
        deferred: DeferredPublicMessageInteractionResponseBehavior,
    ) {

        // component id is in format of action:genericIdentifier
        val genericIdentifier = interaction.componentId.substringAfter(':')
        val conflictMessageWithInfo = conflictMessages[genericIdentifier]
            ?: "Tried to handle conflict button click for unknown manifest $genericIdentifier".let {
                logger.warn { it }
                deferred.respond { content = it }.apply {
                    delay(1000L * 3)
                    this.delete()
                }
                return
            }
        val (conflictMessage, manifestWithCreationStatus) = conflictMessageWithInfo

        when (genericIdentifier) {
            "accept-original:$genericIdentifier" -> {
                "Accepted original manifest of $genericIdentifier".also {
                    deferred.respond { content = it }.apply {
                        logger.debug { }
                        delay(1000L * 3)
                        conflictMessage.delete("Accepted original manifest $genericIdentifier")
                        delete()
                    }
                    logger.debug { it }
                }
                resolvedConflicts.send(
                    manifestWithCreationStatus.copy(
                        reviewStatus = ReviewStatus.APPROVED_UPDATE,
                        latestManifest = manifestWithCreationStatus.originalManifest
                    )
                )
            }

            "accept-latest:$genericIdentifier" -> {
                "Accepted latest manifest of $genericIdentifier".also {
                    deferred.respond { content = it }.apply {
                        delay(1000L * 3)
                        conflictMessage.delete("Accepted latest manifest $genericIdentifier")
                        delete()
                    }
                    logger.debug { it }
                }
                resolvedConflicts.send(manifestWithCreationStatus.copy(reviewStatus = ReviewStatus.APPROVED_UPDATE))
            }

            "merge:$genericIdentifier" -> {
                "Merged manifests".also {
                    deferred.respond { content = it }.apply {
                        delay(1000L * 3)
                        conflictMessage.delete("Merged manifests $genericIdentifier")
                        delete()
                    }

                    logger.debug { it }
                }
                // Prioritize newer content over older content
                resolvedConflicts.send(
                    manifestWithCreationStatus.copy(
                        reviewStatus = ReviewStatus.APPROVED_UPDATE,
                        latestManifest = manifestWithCreationStatus.latestManifest.let { latestManifest ->
                            manifestWithCreationStatus.originalManifest.let { originalManifest ->

                                ManifestJson(
                                    latestManifest?.indexVersion ?: originalManifest.indexVersion,
                                    latestManifest?.genericIdentifier ?: originalManifest.genericIdentifier,
                                    latestManifest?.fancyName ?: originalManifest.fancyName,
                                    latestManifest?.author ?: originalManifest.author,
                                    latestManifest?.license ?: originalManifest.license,
                                    latestManifest?.curseForgeId ?: originalManifest.curseForgeId,
                                    latestManifest?.modrinthId ?: originalManifest.modrinthId,
                                    ManifestLinks(
                                        latestManifest?.links?.issue ?: originalManifest.links?.issue,
                                        latestManifest?.links?.sourceControl
                                            ?: originalManifest.links?.sourceControl,
                                        (latestManifest?.links?.others
                                            ?: emptyList()) + (originalManifest.links?.others ?: emptyList())
                                    ),

                                    (latestManifest?.files?.map { versionFile ->
                                        VersionFile(
                                            versionFile.fileName,
                                            versionFile.mcVersions + (originalManifest.files.firstOrNull { versionFile.sha512Hash == it.sha512Hash }?.mcVersions
                                                ?: emptyList()),
                                            versionFile.sha512Hash,
                                            versionFile.downloadUrls + (originalManifest.files.firstOrNull { versionFile.sha512Hash == it.sha512Hash }?.downloadUrls
                                                ?: emptyList()),
                                            versionFile.curseDownloadAvailable || originalManifest.files.firstOrNull { versionFile.sha512Hash == it.sha512Hash }?.curseDownloadAvailable == true
                                        )
                                    }
                                        ?: emptyList()) +

                                            // Find all files that were not in the latest manifest, and add them. No configuration required, as there is nothing to compare to
                                            originalManifest.files.filter { versionFile ->
                                                versionFile.sha512Hash !in (latestManifest?.files?.map { it.sha512Hash }
                                                    ?: emptyList())
                                            }

                                )
                            }
                        }
                    )
                )
            }

            "dismiss:$genericIdentifier" -> {
                "Dismissed manifests".also {
                    deferred.respond { content = it }.apply {
                        delay(1000L * 3)
                        conflictMessage.delete("Dismissed manifests $genericIdentifier")
                        delete()
                    }
                    logger.debug { it }
                }
                // Just drop the manifests, no need to do anything with it
            }


            else -> {
                deferred.respond { content = "Unknown command ${interaction.componentId}!" }.apply {
                    delay(1000L * 3)
                    delete()
                }
                return
            }
        }

        val maintainerResolvedThread = parentTextChannel.activeThreads.firstOrNull {
            it.name == "maintainer-resolved"
        }

        "Manifest $genericIdentifier has been resolved with method ${interaction.componentId.substringBefore(":")}".also {
            maintainerResolvedThread?.createMessage {
                content = it
            }?.also { logger.debug { it } } ?: logger.warn { "Message to thread \"maintainer-resolved\" failed!" }
        }

    }
}

