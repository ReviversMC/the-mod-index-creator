package com.github.reviversmc.themodindex.creator.ghapp

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthApiCall
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse
import com.github.reviversmc.themodindex.creator.core.creatorModule
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.context.startKoin
import retrofit2.awaitResponse
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

const val COROUTINES_PER_TASK = 5 // Arbitrary number of concurrent downloads. Change if better number is found.

@ExperimentalCoroutinesApi
@ExperimentalSerializationApi
fun main() = runBlocking {

    startKoin {
        modules(
            creatorModule
        )
    }

    val gitHubComponent = GitHubComponent()

    val apiDownloader = gitHubComponent.indexApiDownloader
    val index = apiDownloader.downloadIndexJson() ?: throw IOException("Could not download manifest index")

    val manifestDownloadSemaphore = Semaphore(COROUTINES_PER_TASK)
    val existingManifestIdentifiers = index.identifiers.map { it.substringBeforeLast(":") }
    val existingManifestRequests = existingManifestIdentifiers.distinct().map {
        async {
            manifestDownloadSemaphore.withPermit {
                apiDownloader.downloadManifestJson(it) ?: throw IOException("Could not download manifest $it")
            }
        }
    }

    val modrinthApiCall = gitHubComponent.modrinthApiCall
    val modrinthSearchChannel = produceModrinthSearchResults(modrinthApiCall, doStarterSearch(modrinthApiCall).first())



    val existingManifests = existingManifestRequests.awaitAll()
    /*
    Now that we have all existing manifests, we can start cross-referencing with modrinth & curse results to see what's missing
    Discard Modrinth and Curse ids that are already in the existing manifests.
    Those just need updating, and should be done separately (no need to repair Modrinth and Curse ids)
     */
    val newModrinthProjects = findNewModrinthProjects(existingManifests, modrinthSearchChannel)
        .buffer(COROUTINES_PER_TASK)

    //TODO Collect and create complete clause. Perhaps don't use a flow if that's more advantageous?


}

//TODO timer thread that allocates when to do create manifests
//TODO At pre-planned time of day, create new jobs for the day
//TODO Then, create and push. We are likely be limited by gh rate limits. Maybe use BOTH the rest api and graphql api for more calls?
//TODO Start off with retrieving all current manifests, modrinth projects, and CF mods. Find out what manifests need updating, and what manifests need creating

fun findNewModrinthProjects(
    existingManifests: List<ManifestJson>,
    modrinthSearchChannel: ReceiveChannel<ModrinthResponse.SearchResponse.SearchHit>
) = flow {
    val existingIds = existingManifests.map { it.modrinthId }

    for (modrinthProject in modrinthSearchChannel) {
        if (modrinthProject.id !in existingIds) emit(modrinthProject)
    }
}

fun doStarterSearch(modrinthApiCall: ModrinthApiCall) = flow {
    val searchResponse = modrinthApiCall.search(limit = Int.MAX_VALUE).execute()

    val starterSearch = searchResponse.body() ?: searchResponse.headers().get("x-ratelimit-remaining")?.let {
        if (it.toInt() == 0) {
            delay(searchResponse.headers().get("x-ratelimit-reset")!!.toLong())
            modrinthApiCall.search(limit = Int.MAX_VALUE).execute().body()
        } else throw IOException("Could not search modrinth")
    } ?: throw IOException("Could not search modrinth")

    emit(starterSearch)
}

//We require starter search to be done outside this function, as we need it to designate the buffer capacity.
@ExperimentalCoroutinesApi
fun CoroutineScope.produceModrinthSearchResults(
    modrinthApiCall: ModrinthApiCall, starterSearch: ModrinthResponse.SearchResponse
) = produce(capacity = starterSearch.limit * COROUTINES_PER_TASK) {
    val totalNumOfProjects = starterSearch.totalHits
    val maxSearchSize = starterSearch.limit
    val nextToRequest = AtomicInteger(starterSearch.limit)
    val modrinthRateLimitedSeconds = AtomicLong(0L)

    launch { starterSearch.hits.forEach { send(it) } } //We already have the first batch of results, no need to make another call for it.

    val searchResults = (1..COROUTINES_PER_TASK).map {

        async {
            while (nextToRequest.get() < totalNumOfProjects) {

                //Wait if rate limited. Else, 0L * 1000L == 0L delay
                delay(modrinthRateLimitedSeconds.get() * 1000L)

                val searchResponse = modrinthApiCall.search(
                    offset = nextToRequest.get(), limit = maxSearchSize
                ).awaitResponse()

                searchResponse.body()?.hits?.let { hits ->
                    nextToRequest.addAndGet(hits.size)
                    hits.forEach { send(it) }
                }
                //Check if fail is due to rate limit
                    ?: searchResponse.headers().get("x-ratelimit-remaining")?.let {
                        if (it.toInt() == 0) {
                            modrinthRateLimitedSeconds.set(searchResponse.headers().get("x-ratelimit-reset")!!.toLong())
                        }

                        while (modrinthRateLimitedSeconds.get() > 0) {
                            delay(1000)
                            modrinthRateLimitedSeconds.decrementAndGet()
                        }
                    }

                    //Nope, fail not due to rate limit. Throw exception as we don't know what happened
                    ?: throw IOException("Could not search modrinth")
            }
        }

    }

    searchResults.awaitAll() //Blocks, and waits for all given coroutines to finish (by doing all searches)
    close()
}
