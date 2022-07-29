package com.github.reviversmc.themodindex.creator.maintainer.github

import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val updateSenderModule = module {

    /*
    These are the below referenced variables:
    (manifestRepo: String?, repoOwner: String, repoName: String, targetedBranch: String, gitHubAppId: String, gitHubPrivateKeyPath: String)
    */

    factory {
        GitHubUpdateSender(
            it.get<String?>(0)?.let { manifestRepo -> get(named("custom")) { parametersOf(manifestRepo) } } ?: get(),
            get(),
            it[1],
            it[2],
            it[3],
            it[4],
            get(),
            it[5]
        )
    } bind UpdateSender::class
    includes(dependencyModule)
}
