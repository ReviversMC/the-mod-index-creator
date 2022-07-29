package com.github.reviversmc.themodindex.creator.maintainer.apicalls

import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.core.parameter.parametersOf
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

val githubMaintainerModule = module {
    factory {(githubToken: String, repoOwner: String, repoName: String) ->
        GHGraphQLBranch(get { parametersOf(githubToken) }, repoOwner, repoName)
    } bind GHBranch::class

    factory {
        Retrofit.Builder()
            .addConverterFactory(get())
            .baseUrl("https://api.github.com/")
            .client(get())
            .build()
            .create(GHRestApp::class.java)
    } bind GHRestApp::class

    includes(dependencyModule)
}
