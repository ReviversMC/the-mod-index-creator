package com.github.reviversmc.themodindex.creator.maintainer.data

/**
 * Contains all config values for the-mod-index-maintainer.
 * @param curseForgeApiKey An API key for accessing the CurseForge API.
 * @param discordBotToken The token for the discord bot. (This should be secret!)
 * @param discordServer The discord server the bot should target. The bot should already be in the server.
 * @param discordTextChannel The discord channel the bot should target. This SHOULD NOT be a thread, and the bot should have access to the channel.
 * @param gitHubAppId The GitHub app id.
 * @param gitHubPrivateKeyPath The GitHub app private key path. It is recommended that this should be an absolute path.
 * @param gitHubRepoOwner The GitHub repo owner for the targeted repo.
 * @param gitHubRepoName The GitHub repo name for the targeted repo.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class AppConfig(
    val curseForgeApiKey: String,
    val discordBotToken: String,
    val discordServer: Long,
    val discordTextChannel: Long,
    val gitHubAppId: String,
    val gitHubPrivateKeyPath: String,
    val gitHubRepoOwner: String,
    val gitHubRepoName: String,
)
