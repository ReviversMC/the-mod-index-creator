package com.github.reviversmc.themodindex.creator.ghapp.apicalls

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

private class GitHubAuthInterceptor(val gitHubToken: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().addHeader("Authorization", "bearer $gitHubToken").build()
        return chain.proceed(request)
    }

}

val githubGraphqlModule = module {
    factory(named("githubGraphql")) { (githubToken: String) -> // We are unable to use single, because we need to inject the token, which may be different for each instance
        OkHttpClient.Builder().addInterceptor(GitHubAuthInterceptor(githubToken)).build()
    }

    factory {(githubToken: String) ->
        ApolloClient.Builder().serverUrl("https://api.github.com/graphql").okHttpClient(get(named("githubGraphql")) { parametersOf(githubToken) })
            .build()
    }

    factory {(githubToken: String, repoOwner: String, repoName: String) ->
        GHGraphQLBranch(get { parametersOf(githubToken) }, repoOwner, repoName)
    } bind GHBranch::class
}