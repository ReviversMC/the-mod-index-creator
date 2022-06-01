package com.github.reviversmc.themodindex.creator.cli

import com.github.reviversmc.themodindex.creator.core.apicalls.apiCallModule
import com.github.reviversmc.themodindex.creator.core.creatorModule
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.context.startKoin


@ExperimentalSerializationApi
fun main() {
    startKoin {
        modules(
            apiCallModule,
            creatorModule
        )
    }
}

