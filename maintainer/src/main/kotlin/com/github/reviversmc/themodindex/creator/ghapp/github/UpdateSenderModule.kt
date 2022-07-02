package com.github.reviversmc.themodindex.creator.ghapp.github

import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.dsl.bind
import org.koin.dsl.module

val updateSenderModule = module {

    factory { (repoOwner: String, repoName: String, targetedBranch: String, gitHubAppId: String, gitHubPrivateKeyPath: String) ->
        GitHubUpdateSender(
            get(),
            get(),
            repoOwner,
            repoName,
            targetedBranch,
            gitHubAppId,
            gitHubPrivateKeyPath
        )
    } bind UpdateSender::class
    includes(dependencyModule)
}