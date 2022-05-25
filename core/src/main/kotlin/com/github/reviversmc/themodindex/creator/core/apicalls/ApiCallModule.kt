package com.github.reviversmc.themodindex.creator.core.apicalls

import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

val apiCallModule = module {
    factory {
        Retrofit.Builder()
            .addConverterFactory(get())
            .baseUrl("https://api.curseforge.com/v1")
            .client(get())
            .build()
            .create(CurseForgeApiCall::class.java)
    } bind CurseForgeApiCall::class

    factory {
        Retrofit.Builder()
            .addConverterFactory(get())
            .baseUrl("https://api.modrinth.com/v2")
            .client(get())
            .build()
            .create(ModrinthApiCall::class.java)
    } bind ModrinthApiCall::class
}