package com.github.reviversmc.themodindex.creator.core

import com.apollographql.apollo3.ApolloClient
import com.github.reviversmc.themodindex.creator.core.apicalls.apiCallModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val creatorModule = module {
    single { (customRepo: String, curseForgeApiKey: String, refreshGitHubClient: () -> ApolloClient) ->
        ModIndexCreator(
            get(named("custom")) { parametersOf(customRepo) },
            curseForgeApiKey,
            get(),
            refreshGitHubClient,
            get(),
            get()
        )
    } bind Creator::class
    includes(apiCallModule, dependencyModule)
}