package com.github.reviversmc.themodindex.creator.core.apicalls

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val apiCallModule = module {
    factoryOf(::CurseForgeCoreV1ApiCall) bind CurseForgeApiCall::class
    factoryOf(::ModrinthV2ApiCall) bind ModrinthApiCall::class
}