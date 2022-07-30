package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.apollographql.apollo3.ApolloClient
import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import com.github.reviversmc.themodindex.creator.maintainer.RunMode
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val manifestReviewModule = module {
    factory { (manifestRepo: String, curseApiKey: String, refreshGitHubClient: () -> ApolloClient, runMode: RunMode) ->
        IndexExistingManifestReviewer(
            get(named("custom")) { parametersOf(manifestRepo) },
            get { parametersOf(curseApiKey, refreshGitHubClient) },
            runMode
        )
    } bind ExistingManifestReviewer::class

    factory(named("curseforge")) { (manifestRepo: String, curseApiKey: String, refreshGitHubClient: () -> ApolloClient, runMode: RunMode) ->
        CurseForgeManifestReviewer(
            get(named("custom")) { parametersOf(manifestRepo, refreshGitHubClient) },
            get { parametersOf(curseApiKey, refreshGitHubClient) },
            get(),
            curseApiKey,
            runMode
        )
    } bind NewManifestReviewer::class

    factory(named("modrinth")) { (manifestRepo: String, curseApiKey: String, refreshGitHubClient: () -> ApolloClient, runMode: RunMode) ->
        ModrinthManifestReviewer(
            get(named("custom")) { parametersOf(manifestRepo) },
            get { parametersOf(curseApiKey, refreshGitHubClient) },
            get(),
            runMode
        )
    } bind NewManifestReviewer::class

    includes(creatorModule, dependencyModule)
}