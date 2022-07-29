package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.apicalls.CurseForgeApiCall
import com.github.reviversmc.themodindex.creator.core.apicalls.CurseModData
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import com.github.reviversmc.themodindex.creator.maintainer.FLOW_BUFFER
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestPendingReview
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.maintainer.data.ReviewStatus
import io.ktor.network.sockets.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import java.io.IOException

class CurseForgeManifestReviewer(
    private val apiDownloader: ApiDownloader,
    private val creator: Creator,
    private val curseForgeApiCall: CurseForgeApiCall,
    private val curseForgeApiKey: String,
    private val testMode: Boolean,
) : NewManifestReviewer {

    private val logger = KotlinLogging.logger {}

    private suspend fun obtainCurseForgeInfo() = flow {
        logger.debug { "Obtaining CurseForge info..." }

        val existingCurseIds = mutableListOf<Int>().apply {
            apiDownloader.downloadExistingManifests(logger).buffer(FLOW_BUFFER).collect { existingManifest ->
                existingManifest.curseForgeId?.let { add(it) }
            }
            logger.debug { "Downloaded existing manifests" }
        }.toList()


        val firstSearch = curseForgeApiCall.search(curseForgeApiKey, 0).execute().body()
            ?: throw IOException("No response from CurseForge")
        logger.debug { "Made first search to CurseForge" }
        val limitPerSearch = firstSearch.pagination.pageSize

        val totalCount = if (testMode) 20 // Just test with a small number of results
        else firstSearch.pagination.totalCount // Otherwise, use the total count from the first search

        logger.debug { "Total of $totalCount CurseForge mods found" }

        var offset = 0

        while (offset < totalCount) {
            val search = (if (testMode) curseForgeApiCall.search(curseForgeApiKey, offset, totalCount).execute().body()
            else curseForgeApiCall.search(curseForgeApiKey, offset).execute().body())
                ?: throw IOException("No response from CurseForge")

            offset += limitPerSearch

            if (search.data.isEmpty()) break

            search.data.forEach {
                if (it.id !in existingCurseIds) {
                    emit(it) // emit the response, not the id
                    logger.debug { "Found new CurseForge project ${it.id}" }
                }
            }
        }
        logger.debug { "Finished obtaining CurseForge info" }
    }

    private suspend fun createManifests(inputFlow: Flow<CurseModData>) = flow {
        logger.debug { "Creating CurseForge manifests..." }
        var counter = 0
        inputFlow.collect { curseForgeMod ->
            logger.debug { "($counter) Creating manifest for CurseForge project ${curseForgeMod.id}" }

            val createdManifests = try {
                creator.createManifestCurseForge(curseForgeMod)
            } catch (_: SocketTimeoutException) {
                logger.warn { "($counter) Socket timeout while creating manifest for CurseForge project ${curseForgeMod.id}" }
                return@collect
            }

            logger.debug { "($counter) Created manifest for CurseForge project ${curseForgeMod.id}" }
            createdManifests.manifests.forEach {
                emit(ManifestPendingReview(createdManifests.thirdPartyApiUsage, it, it))
            }
            ++counter
        }
    }

    override fun reviewManifests() = flow {
        val createdManifests = createManifests(obtainCurseForgeInfo())

        createdManifests.collect { (thirdPartyApiStatus, latestManifest, originalManifest) ->
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