package com.github.reviversmc.themodindex.creator.core.dependency

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dependencyModule = module {
    factory {
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
    singleOf(::OkHttpClient)
}