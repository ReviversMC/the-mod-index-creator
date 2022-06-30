package com.github.reviversmc.themodindex.creator.ghapp.github

import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.dsl.bind
import org.koin.dsl.module

val updateSenderModule = module {

    factory {
        GitHubUpdateSender(
            get(),
            get(),
            repoName = it.get(),
            repoOwner = it[1],
            targetedBranch = it[2],
            gitHubAppId = it[3],
            gitHubPrivateKeyPath = it[4],
            prInsteadOfPush = it.get(),
        )
    } bind UpdateSender::class
    includes(dependencyModule)
}