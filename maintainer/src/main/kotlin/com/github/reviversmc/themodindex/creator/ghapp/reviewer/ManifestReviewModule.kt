package com.github.reviversmc.themodindex.creator.ghapp.reviewer

import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val manifestReviewModule = module {
    factory { (manifestRepo: String, curseApiKey: String, gitHubApiKey: String) ->
        IndexExistingManifestReviewer(get(named("custom")) { parametersOf(manifestRepo) },
            get { parametersOf(curseApiKey, gitHubApiKey) })
    } bind ExistingManifestReviewer::class

    factory(named("curseforge")) { (manifestRepo: String, curseApiKey: String, gitHubApiKey: String) ->
        CurseForgeManifestReviewer(get(named("custom")) { parametersOf(manifestRepo) },
            get { parametersOf(curseApiKey, gitHubApiKey) },
            get(),
            curseApiKey
        )
    } bind NewManifestReviewer::class

    factory(named("modrinth")) { (manifestRepo: String?, curseApiKey: String, gitHubApiKey: String) ->
        ModrinthManifestReviewer(get(named("custom")) { parametersOf(manifestRepo) },
            get { parametersOf(curseApiKey, gitHubApiKey) },
            get()
        )
    } bind NewManifestReviewer::class

    includes(creatorModule, dependencyModule)
}