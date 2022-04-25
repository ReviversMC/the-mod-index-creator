package com.github.reviversmc.themodindex.creator.core.apicalls

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val apiCallModule = module {
    factoryOf(::ModrinthV2ApiCall) bind ModrinthApiCall::class
}