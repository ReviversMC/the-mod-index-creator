package com.github.reviversmc.themodindex.creator.ghapp.reviewer

import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val manifestReviewModule = module {
    factoryOf(::ExistingManifestReviewer) bind ManifestReviewer::class
    includes(creatorModule, dependencyModule)
}