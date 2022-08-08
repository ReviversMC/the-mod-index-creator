package com.github.reviversmc.themodindex.creator.core.dependency

import cc.ekblad.toml.tomlMapper
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.api.downloader.DefaultApiDownloader
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(ExperimentalSerializationApi::class)
val dependencyModule = module {
    factory { (get() as Json).asConverterFactory("application/json".toMediaType()) }
    factory { DefaultApiDownloader(okHttpClient = get(), json = get()) } bind ApiDownloader::class
    factory(named("custom")) { (customRepo: String) -> DefaultApiDownloader(customRepo, get(), get()) } bind ApiDownloader::class
    factory {
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }

    factory { tomlMapper {} }
    singleOf(::OkHttpClient)
}