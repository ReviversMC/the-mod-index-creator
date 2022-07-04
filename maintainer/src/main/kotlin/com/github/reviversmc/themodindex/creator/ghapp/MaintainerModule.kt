package com.github.reviversmc.themodindex.creator.ghapp

import kotlinx.cli.ArgParser
import org.koin.dsl.module

val appModule = module {
    single { ArgParser("the-mod-index-maintainer") }
}