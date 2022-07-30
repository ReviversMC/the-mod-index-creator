package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthApiCall
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import com.github.reviversmc.themodindex.creator.maintainer.FLOW_BUFFER
import com.github.reviversmc.themodindex.creator.maintainer.RunMode
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestPendingReview
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.maintainer.data.ReviewStatus
import io.ktor.network.sockets.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import retrofit2.HttpException
import java.io.IOException

class ModrinthManifestReviewer(
    private val apiDownloader: ApiDownloader,
    private val creator: Creator,
    private val existingManifests: List<ManifestJson>,
    private val modrinthApiCall: ModrinthApiCall,
    private val runMode: RunMode,
) : NewManifestReviewer {

    private val logger = KotlinLogging.logger {}

    private suspend fun obtainModrinthInfo() = flow {
        logger.debug { "Obtaining Modrinth info..." }

        val existingModrinthIds = mutableListOf<String>().apply {
            existingManifests.forEach { existingManifest ->
                existingManifest.modrinthId?.let { add(it) }
            }
            logger.debug { "Downloaded existing manifests" }
        }.toList()

        var limitPerSearch = Int.MAX_VALUE
        val firstSearch = modrinthApiCall.search(limit = limitPerSearch).execute().body()
            ?: throw IOException("No response from Modrinth")
        logger.debug { "Made first search to Modrinth" }
        limitPerSearch = firstSearch.limit

        val totalCount = if (runMode == RunMode.TEST_SHORT) 20 // Just test with a small number of results
        else firstSearch.totalHits // Otherwise, use the total count from the first search

        logger.debug { "Total of $totalCount Modrinth projects found" }

        var offset = 0

        while (offset < totalCount) {

            offset += limitPerSearch

            val search = try {
                if (runMode == RunMode.TEST_SHORT) modrinthApiCall.search(
                    /*
                    In short test mode, don't go by default search.
                    Mods like FAPI have a LOT of versions, and that takes a while to generate manifests for
                     */
                    searchMethod = ModrinthApiCall.SearchMethod.NEWEST.modrinthString,
                    limit = totalCount
                ).execute().body()
                else modrinthApiCall.search(limit = limitPerSearch, offset = offset).execute().body()
            } catch (_: SocketTimeoutException) {
                logger.warn { "Modrinth search timed out" }
                continue // Skip the search, we don't want to get stuck waiting on the api
            } ?: throw IOException("No response from Modrinth")


            if (search.hits.isEmpty()) break

            search.hits.forEach {
                if (it.id !in existingModrinthIds) {
                    emit(it.id)
                    logger.debug { "Found new Modrinth project ${it.id}" }
                }
            }
        }
        logger.debug { "Finished obtaining Modrinth info" }
    }


    private suspend fun createManifests(inputFlow: Flow<String>) = flow {
        logger.debug { "Creating Modrinth manifests..." }
        var counter = 0
        inputFlow.collect { modrinthId ->
            logger.debug { "($counter) Creating manifest for Modrinth project $modrinthId" }

            val createdManifests = try {
                creator.createManifestModrinth(modrinthId)
            } catch (_: SocketTimeoutException) {
                logger.warn { "($counter) Socket timeout while creating manifest for Modrinth project $modrinthId" }
                null
            } catch (ex: HttpException) {
                logger.warn { "($counter) HTTP exception while creating manifest for Modrinth project $modrinthId: code ${ex.code()}" }
                null
            } ?: run {
                ++counter
                return@collect
            }

            logger.debug { "($counter) Created manifest for Modrinth project $modrinthId" }
            createdManifests.manifests.forEach {
                emit(ManifestPendingReview(createdManifests.thirdPartyApiUsage, it, it))
            }
            ++counter
        }
    }

    override fun reviewManifests() = flow {
        val createdManifests = createManifests(obtainModrinthInfo().buffer(FLOW_BUFFER))

        createdManifests.buffer(FLOW_BUFFER).collect { (thirdPartyApiStatus, latestManifest, originalManifest) ->
            if (ThirdPartyApiUsage.MODRINTH_USED !in thirdPartyApiStatus) {
                emit(
                    ManifestWithCreationStatus(
                        ReviewStatus.THIRD_PARTY_API_FAILURE, latestManifest, originalManifest
                    )
                )
                logger.debug { "Third party api failure for ${originalManifest.genericIdentifier}" }
            } else {
                val existingManifestInRepo = apiDownloader.downloadManifestJson(originalManifest.genericIdentifier)
                if (existingManifestInRepo == null) { // Means that this is the first copy
                    emit(ManifestWithCreationStatus(ReviewStatus.APPROVED_UPDATE, latestManifest, originalManifest))
                    logger.debug { "Creation of ${originalManifest.genericIdentifier} is approved" }
                } else {
                    emit(
                        ManifestWithCreationStatus(
                            ReviewStatus.UPDATE_CONFLICT,
                            latestManifest,
                            existingManifestInRepo
                        )
                    )
                    logger.warn { "Conflict detected: Created ${originalManifest.genericIdentifier} instead of updating an existing version" }
                }
            }
        }

    }
}