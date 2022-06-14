package com.github.reviversmc.themodindex.creator.core.apicalls

import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

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
}