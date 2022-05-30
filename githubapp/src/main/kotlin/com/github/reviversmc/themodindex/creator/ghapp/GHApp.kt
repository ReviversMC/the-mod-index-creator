package com.github.reviversmc.themodindex.creator.ghapp

import com.github.reviversmc.themodindex.creator.core.creatorModule
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.context.startKoin

@ExperimentalSerializationApi
fun main() {

    startKoin {
        modules(
            creatorModule
        )
    }

    //TODO timer thread that allocates when to do create manifests
    //TODO At pre-planned time of day, create new jobs for the day
    //TODO Then, create and push. We are likely be limited by gh rate limits. Maybe use BOTH the rest api and graphql api for more calls?
    //TODO Start off with retrieving all current manifests, modrinth projects, and CF mods. Find out what manifests need updating, and what manifests need creating
}
