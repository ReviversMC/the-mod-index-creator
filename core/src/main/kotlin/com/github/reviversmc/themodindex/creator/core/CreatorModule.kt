package com.github.reviversmc.themodindex.creator.core

import com.github.reviversmc.themodindex.creator.core.apicalls.apiCallModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
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
    includes(apiCallModule, dependencyModule)
}