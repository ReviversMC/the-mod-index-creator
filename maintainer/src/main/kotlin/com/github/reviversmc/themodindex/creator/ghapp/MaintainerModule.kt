package com.github.reviversmc.themodindex.creator.ghapp

import dev.kord.core.Kord
import kotlinx.cli.ArgParser
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module

val appModule = module {
    single { ArgParser("the-mod-index-maintainer") }
    single { (botToken: String) -> runBlocking { Kord(botToken) } }
}