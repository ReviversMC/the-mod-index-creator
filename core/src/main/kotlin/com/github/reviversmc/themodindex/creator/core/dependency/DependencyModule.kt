package com.github.reviversmc.themodindex.creator.core.dependency

import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.api.downloader.DefaultApiDownloader
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.OkHttpClient
import org.kohsuke.github.GitHubBuilder
import org.kohsuke.github.connector.GitHubConnector
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

@ExperimentalSerializationApi
val dependencyModule = module {
    factory { (get() as Json).asConverterFactory(MediaType.get("application/json")) }
    factory { DefaultApiDownloader(get()) } bind ApiDownloader::class //Use the old DSL so that we can specify which params to fill.
    factory {
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
    factory { (oAuthToken: String) ->
        GitHubBuilder().withOAuthToken(oAuthToken).withConnector(get() as GitHubConnector).build()
    }
    factory { OkHttpGitHubConnector(get()) } bind GitHubConnector::class
    singleOf(::OkHttpClient)
}