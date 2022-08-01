package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val manifestReviewModule = module {
    /*
    Params for all the below are:
    manifestRepo: String,
    curseApiKey: String,
    refreshGitHubClient: () -> ApolloClient,
    existingManifests: List<ManifestJson>,
    runMode: RunMode
    operationModes: List<OperationMode>
    */

    factory {
        IndexExistingManifestReviewer(
            get(named("custom")) { parametersOf(it[0]) },
            get { parametersOf(it[1], it[2]) },
            it[3],
            it[4],
            it[5]
        )
    } bind ExistingManifestReviewer::class

    factory(named("curseforge")) {
        CurseForgeManifestReviewer(
            get(named("custom")) { parametersOf(it[0]) },
            get { parametersOf(it[1], it[2]) },
            get(),
            it[1],
            it[3],
            it[4],
            it[5]
        )
    } bind NewManifestReviewer::class

    factory(named("modrinth")) {
        ModrinthManifestReviewer(
            get(named("custom")) { parametersOf(it[0]) },
            get { parametersOf(it[1], it[2]) },
            it[3],
            get(),
            it[4],
            it[5]
        )
    } bind NewManifestReviewer::class

    includes(creatorModule, dependencyModule)
}