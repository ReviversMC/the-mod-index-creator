package com.github.reviversmc.themodindex.creator.core

import com.github.reviversmc.themodindex.creator.core.apicalls.apiCallModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.kohsuke.github.GitHub
import org.koin.dsl.bind
import org.koin.dsl.module

val creatorModule = module {
    factory { (curseForgeApiKey: String, refreshGitHubClient: () -> GitHub) ->
        ModIndexCreator(
            get(), curseForgeApiKey, get(), refreshGitHubClient, get(), get()
        )
    } bind Creator::class
    includes(apiCallModule, dependencyModule)
}