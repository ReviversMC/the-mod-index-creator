package com.github.reviversmc.themodindex.creator.core.apicalls

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

private class GitHubAuthInterceptor(private val gitHubToken: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().addHeader("Authorization", "bearer $gitHubToken").build()
        return chain.proceed(request)
    }
}

val apiCallModule = module {
    factory {
        Retrofit.Builder()
            .addConverterFactory(get())
            .baseUrl("https://api.curseforge.com/")
            .client(get())
            .build()
            .create(CurseForgeApiCall::class.java)
    } bind CurseForgeApiCall::class

    factory {
        Retrofit.Builder()
            .addConverterFactory(get())
            .baseUrl("https://api.modrinth.com/")
            .client(get())
            .build()
            .create(ModrinthApiCall::class.java)
    } bind ModrinthApiCall::class
    includes(dependencyModule)

    factory(named("githubGraphql")) { (githubToken: String) -> // We are unable to use single, because we need to inject the token, which may be different for each instance
        OkHttpClient.Builder().addInterceptor(GitHubAuthInterceptor(githubToken)).build()
    }

    factory { (githubToken: String) ->
        ApolloClient.Builder().serverUrl("https://api.github.com/graphql")
            .okHttpClient(get(named("githubGraphql")) { parametersOf(githubToken) })
            .build()
    }
}
