package com.github.reviversmc.themodindex.creator.core.dependency

import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.api.downloader.DefaultApiDownloader
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val dependencyModule = module {
    factory {
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
    factory { DefaultApiDownloader(get()) } bind ApiDownloader::class
    singleOf(::OkHttpClient)
}