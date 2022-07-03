package com.github.reviversmc.themodindex.creator.ghapp.data

@kotlinx.serialization.Serializable
data class AppConfig(
    val discordBotToken: String,
    val discordServer: Long,
    val discordTextChannel: Long,
    val gitHubAppId: String,
    val gitHubPrivateKeyPath: String,
    val gitHubRepoOwner: String,
    val gitHubRepoName: String,
)
