package com.github.reviversmc.themodindex.creator.ghapp.data

@kotlinx.serialization.Serializable
data class AppConfig(
    val discordBotToken: String,
    val discordChannel: Long,
    val discordServer: Long,
    val gitHubAppId: String,
    val gitHubPrivateKeyPath: String,
    val targetedGitHubRepoOwner: String,
    val targetedGitHubRepoName: String,
)
