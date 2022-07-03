package com.github.reviversmc.themodindex.creator.ghapp.reviewer

import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.apicalls.CurseForgeApiCall
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import com.github.reviversmc.themodindex.creator.ghapp.COROUTINES_PER_TASK
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestPendingReview
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.ghapp.data.ReviewStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class CurseForgeManifestReviewer(
    private val apiDownloader: ApiDownloader,
    private val creator: Creator,
    private val curseForgeApiCall: CurseForgeApiCall,
    private val curseForgeApiKey: String,
) : NewManifestReviewer {

    private val logger = KotlinLogging.logger {}

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun obtainCurseForgeInfo() = coroutineScope {
        logger.debug { "Obtaining CurseForge info..." }
        produce(capacity = COROUTINES_PER_TASK * 2) {

            val existingCurseIdsDeferred = async {
                val returnList = mutableListOf<Int>()
                for (existingManifest in apiDownloader.downloadExistingManifests(logger)) {
                    existingManifest.curseForgeId?.let { returnList.add(it) }
                }
                return@async returnList
            }

            val firstSearch = curseForgeApiCall.search(curseForgeApiKey, 0).execute().body()
                ?: throw IOException("No response from CurseForge")
            logger.debug { "Made first search to modrinth" }
            val limitPerSearch = firstSearch.pagination.pageSize
            val totalCount = firstSearch.pagination.totalCount
            logger.debug { "Total of $totalCount CurseForge mods found" }

            val totalOffset = AtomicInteger(firstSearch.pagination.pageSize)

            coroutineScope {
                repeat(COROUTINES_PER_TASK) {
                    launch {
                        val offset = totalOffset.getAndAdd(limitPerSearch)
                        while (offset < totalCount) {
                            val search = curseForgeApiCall.search(curseForgeApiKey, offset).execute().body()
                                ?: throw IOException("No response from CurseForge")

                            if (search.data.isEmpty()) return@launch

                            val existingCurseIds = existingCurseIdsDeferred.await()
                            search.data.forEach { if (it.id !in existingCurseIds) {
                                send(it.id.toString())
                                logger.debug { "Found new CurseForge project ${it.id}" }
                            } }
                        }
                    }
                }
            }
            close()
            logger.debug { "Finished obtaining CurseForge info" }
        }
    }

    override suspend fun createManifests(
        inputChannel: ReceiveChannel<String>,
        outputChannel: SendChannel<ManifestPendingReview>,
    ) {
        coroutineScope {
            repeat(COROUTINES_PER_TASK) {
                launch {
                    for (curseForgeId in inputChannel) {
                        val createdManifests = creator.createManifestCurseForge(curseForgeId.toInt())
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
        val curseForgeIds = obtainCurseForgeInfo()
        val createdManifestChannel = Channel<ManifestPendingReview>(COROUTINES_PER_TASK * 2)
        createManifests(curseForgeIds, createdManifestChannel)

        for ((thirdPartyApiStatus, latestManifest, originalManifest) in createdManifestChannel) {
            if (ThirdPartyApiUsage.CURSEFORGE_USED !in thirdPartyApiStatus) {
                emit(ManifestWithCreationStatus(ReviewStatus.THIRD_PARTY_API_FAILURE, latestManifest, originalManifest))
                logger.debug { "Third party api failure for ${originalManifest.genericIdentifier}" }
            } else {
                emit(ManifestWithCreationStatus(ReviewStatus.APPROVED_UPDATE, latestManifest, originalManifest))
                logger.debug { "Creation of ${originalManifest.genericIdentifier} is approved" }
            }
        }
    }
}