package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.kohsuke.github.GitHub
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val manifestReviewModule = module {
    factory { (manifestRepo: String, curseApiKey: String, refreshGitHubClient: () -> GitHub, testMode: Boolean) ->
        IndexExistingManifestReviewer(
            get(named("custom")) { parametersOf(manifestRepo) },
            get { parametersOf(curseApiKey, refreshGitHubClient) },
            testMode
        )
    } bind ExistingManifestReviewer::class

    factory(named("curseforge")) { (manifestRepo: String, curseApiKey: String, refreshGitHubClient: () -> GitHub, testMode: Boolean) ->
        CurseForgeManifestReviewer(
            get(named("custom")) { parametersOf(manifestRepo, refreshGitHubClient) },
            get { parametersOf(curseApiKey, refreshGitHubClient) },
            get(),
            curseApiKey,
            testMode
        )
    } bind NewManifestReviewer::class

    factory(named("modrinth")) { (manifestRepo: String, curseApiKey: String, refreshGitHubClient: () -> GitHub, testMode: Boolean) ->
        ModrinthManifestReviewer(
            get(named("custom")) { parametersOf(manifestRepo) },
            get { parametersOf(curseApiKey, refreshGitHubClient) },
            get(),
            testMode
        )
    } bind NewManifestReviewer::class

    includes(creatorModule, dependencyModule)
}