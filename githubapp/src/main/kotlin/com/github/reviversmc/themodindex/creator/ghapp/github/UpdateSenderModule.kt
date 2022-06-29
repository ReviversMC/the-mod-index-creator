package com.github.reviversmc.themodindex.creator.ghapp.github

import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import com.github.reviversmc.themodindex.creator.ghapp.data.AppConfig
import org.koin.dsl.bind
import org.koin.dsl.module

val updateSenderModule = module {
    factory {(config: AppConfig) ->
        GitHubUpdateSender(get(), get(), config, get())
    } bind UpdateSender::class
    includes(dependencyModule)
}