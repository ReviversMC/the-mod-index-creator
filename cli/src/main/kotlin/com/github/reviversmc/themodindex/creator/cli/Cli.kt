package com.github.reviversmc.themodindex.creator.cli

import com.github.reviversmc.themodindex.creator.core.apicalls.apiCallModule
import com.github.reviversmc.themodindex.creator.core.creatorModule
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.core.context.startKoin


fun main() {
    startKoin {
        modules(
            apiCallModule,
            creatorModule,
            dependencyModule
        )
    }
}

