package com.github.reviversmc.themodindex.creator.ghapp.discordbot

import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.channel.TextChannel
import kotlinx.coroutines.runBlocking
import org.koin.core.parameter.parametersOf
import org.koin.dsl.bind
import org.koin.dsl.module

val discordBotModule = module {
    single { (botToken: String) -> runBlocking { Kord(botToken) } }
    single { (botToken: String, guildId: Snowflake, parentTextChannel: TextChannel) ->
        ModIndexMaintainerBot(
            get(),
            get { parametersOf(botToken) },
            guildId,
            parentTextChannel
        )
    } bind MaintainerBot::class

    includes(dependencyModule)
}