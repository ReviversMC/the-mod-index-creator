package com.github.reviversmc.themodindex.creator.maintainer.apicalls

import com.apollographql.apollo3.ApolloClient
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

val githubMaintainerModule = module {
    factory {(refreshApolloClient: () -> ApolloClient, repoOwner: String, repoName: String) ->
        GHGraphQLBranch(refreshApolloClient, repoOwner, repoName)
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
