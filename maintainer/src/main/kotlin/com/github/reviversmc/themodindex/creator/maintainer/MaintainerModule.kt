package com.github.reviversmc.themodindex.creator.maintainer

import kotlinx.cli.ArgParser
import org.koin.dsl.module

val appModule = module {
    single { ArgParser("the-mod-index-maintainer") }
}