package com.github.reviversmc.themodindex.creator.ghapp.discordbot

import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.ghapp.data.ReviewStatus
import kotlinx.coroutines.channels.Channel

/**
 * This class is used to communicate with the Discord bot.
 * In order to start it, call [start]. The bot will not start on its own.
 * @author ReviversMC
 * @since 1.0.0
 */
interface MaintainerBot {

    /**
     * When conflicts have been manually resolved, the results will be sent here.
     * This should only contain results which are [ReviewStatus.APPROVED_UPDATE]
     * @author ReviversMC
     * @since 1.0.0
     */
    val resolvedConflicts: Channel<ManifestWithCreationStatus>

    /**
     * Make the Discord bot cleanup and shutdown
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun exit(exitMessage: String = "The maintainer is now **offline**, successful shutdown", exitCode: Int = 0)

    /**
     * Setup and start the Discord Bot. This makes the bot ready to receive commands as well
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun start()

    /**
     * Send a [manifestWithCreationStatus] to the Discord bot to be processed for manual review.
     * If a resolution is made, it will be sent to [resolvedConflicts]
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun sendConflict(manifestWithCreationStatus: ManifestWithCreationStatus)

}