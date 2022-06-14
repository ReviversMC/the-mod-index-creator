package com.github.reviversmc.themodindex.creator.cli

import com.github.reviversmc.themodindex.creator.core.creatorModule
import org.koin.core.context.startKoin


fun main() {
    startKoin {
        modules(
            creatorModule
        )
    }
}

