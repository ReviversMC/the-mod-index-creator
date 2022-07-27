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
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(ExperimentalSerializationApi::class)
val dependencyModule = module {
    factory { (get() as Json).asConverterFactory(MediaType.get("application/json")) }
    factory { DefaultApiDownloader(get(), json = get()) } bind ApiDownloader::class
    factory(named("custom")) { (customRepo: String) -> DefaultApiDownloader(get(), customRepo, get()) } bind ApiDownloader::class
    factory {
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }

    factory { (oAuthToken: String) ->
        GitHubBuilder().withJwtToken(oAuthToken).withConnector(get() as GitHubConnector).build()
    }

    factory(named("default")) {
        GitHubBuilder().withConnector(get() as GitHubConnector).build()
    }

    factory { OkHttpGitHubConnector(get()) } bind GitHubConnector::class
    singleOf(::OkHttpClient)
}