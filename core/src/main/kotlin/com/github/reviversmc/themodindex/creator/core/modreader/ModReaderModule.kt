package com.github.reviversmc.themodindex.creator.core.modreader

import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.InputStream

val modReaderModule = module {
    factory { (modJar: InputStream) -> ModMatcher(modJar, get(), get()) } bind ModFile::class
    includes(dependencyModule)
}
