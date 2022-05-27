package com.github.reviversmc.themodindex.creator.core

import org.koin.core.parameter.parametersOf
import org.koin.dsl.bind
import org.koin.dsl.module

val creatorModule = module {
    factory { (curseForgeApiKey: String, gitHubApiKey: String) ->
        ModIndexCreator(
            get(),
            curseForgeApiKey,
            get(),
            get { parametersOf(gitHubApiKey) },
            get(),
            get()
        )
    } bind Creator::class
}