package com.github.reviversmc.themodindex.creator.core

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val creatorModule = module {
    factoryOf(::ModIndexCreator) bind Creator::class
}