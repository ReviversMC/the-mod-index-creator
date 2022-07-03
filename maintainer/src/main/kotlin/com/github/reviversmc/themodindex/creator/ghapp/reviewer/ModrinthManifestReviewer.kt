package com.github.reviversmc.themodindex.creator.ghapp.reviewer

import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthApiCall
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import com.github.reviversmc.themodindex.creator.ghapp.COROUTINES_PER_TASK
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestPendingReview
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.ghapp.data.ReviewStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ModrinthManifestReviewer(
    private val apiDownloader: ApiDownloader,
    private val creator: Creator,
    private val modrinthApiCall: ModrinthApiCall,
) : NewManifestReviewer {

    private val logger = KotlinLogging.logger {}

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun obtainModrinthInfo() = coroutineScope {
        logger.debug { "Obtaining Modrinth info..." }
        produce(capacity = COROUTINES_PER_TASK * 2) {

            val existingModrinthIdsDeferred = async {
                val returnList = mutableListOf<String>()
                for (existingManifest in apiDownloader.downloadExistingManifests(logger)) {
                    existingManifest.modrinthId?.let { returnList.add(it) }
                }
                return@async returnList
            }

            var limitPerSearch = Int.MAX_VALUE
            val firstSearch = modrinthApiCall.search(limit = limitPerSearch).execute().body()
                ?: throw IOException("No response from modrinth")
            logger.debug { "Made first search to modrinth" }
            limitPerSearch = firstSearch.limit
            val totalCount = firstSearch.totalHits
            logger.debug { "Total of $totalCount Modrinth projects found" }

            val totalOffset = AtomicInteger(firstSearch.limit)

            coroutineScope {
                repeat(COROUTINES_PER_TASK) {
                    launch {
                        val offset = totalOffset.getAndAdd(limitPerSearch)
                        while (offset < totalCount) {
                            val search =
                                modrinthApiCall.search(limit = limitPerSearch, offset = offset).execute().body()
                                    ?: throw IOException("No response from modrinth")

                            if (search.hits.isEmpty()) return@launch

                            val existingModrinthIds = existingModrinthIdsDeferred.await()
                            search.hits.forEach {
                                if (it.id !in existingModrinthIds) {
                                    send(it.id)
                                    logger.debug { "Found new Modrinth project ${it.id}" }
                                }
                            }
                        }
                    }
                }
            }
            close()
            logger.debug { "Finished obtaining modrinth info" }
        }
    }

    override suspend fun createManifests(
        inputChannel: ReceiveChannel<String>,
        outputChannel: SendChannel<ManifestPendingReview>,
    ) {
        coroutineScope {
            repeat(COROUTINES_PER_TASK) {
                launch {
                    for (modrinthId in inputChannel) {
                        val createdManifests = creator.createManifestModrinth(modrinthId)
                        createdManifests.manifests.forEach {
                            outputChannel.send(
                                ManifestPendingReview(createdManifests.thirdPartyApiUsage, it, it)
                            )
                        }
                    }
                }
            }
        }
        outputChannel.close()
    }


    override fun reviewManifests() = flow {
        val modrinthIds = obtainModrinthInfo()
        val createdManifestChannel = Channel<ManifestPendingReview>(COROUTINES_PER_TASK * 2)
        createManifests(modrinthIds, createdManifestChannel)

        for ((thirdPartyApiStatus, latestManifest, originalManifest) in createdManifestChannel) {
            if (ThirdPartyApiUsage.MODRINTH_USED !in thirdPartyApiStatus) {
                emit(ManifestWithCreationStatus(ReviewStatus.THIRD_PARTY_API_FAILURE, latestManifest, originalManifest))
                logger.debug { "Third party api failure for ${originalManifest.genericIdentifier}" }
            } else {
                emit(ManifestWithCreationStatus(ReviewStatus.APPROVED_UPDATE, latestManifest, originalManifest))
                logger.debug { "Creation of ${originalManifest.genericIdentifier} is approved" }
            }
        }

    }
}