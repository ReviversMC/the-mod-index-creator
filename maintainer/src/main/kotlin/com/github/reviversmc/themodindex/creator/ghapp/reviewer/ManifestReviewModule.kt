package com.github.reviversmc.themodindex.creator.ghapp.reviewer

import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val manifestReviewModule = module {
    factory { (manifestRepo: String, curseApiKey: String, gitHubApiKey: String) ->
        ExistingManifestReviewer(
            get(named("custom")) { parametersOf(manifestRepo) },
            get { parametersOf(curseApiKey, gitHubApiKey) })
    } bind ManifestReviewer::class

    includes(creatorModule, dependencyModule)
}