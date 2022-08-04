package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthApiCall
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import com.github.reviversmc.themodindex.creator.maintainer.FLOW_BUFFER
import com.github.reviversmc.themodindex.creator.maintainer.OperationMode
import com.github.reviversmc.themodindex.creator.maintainer.RunMode
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestPendingReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class ModrinthManifestReviewer(
    private val apiDownloader: ApiDownloader,
    private val creator: Creator,
    private val existingManifests: List<ManifestJson>,
    private val modrinthApiCall: ModrinthApiCall,
    private val runMode: RunMode,
    private val operationModes: List<OperationMode>,
) : NewManifestReviewer() {

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

        var offset = limitPerSearch
        firstSearch.hits.forEach { // Use the first search, since we already have the data
            if (it.id !in existingModrinthIds) {
                emit(it.id)
                logger.debug { "Found new Modrinth project ${it.id}" }
            }
        }

        var attempts = 0

        while (offset < totalCount) {

            val search = try {
                (if (runMode == RunMode.TEST_SHORT) modrinthApiCall.search(
                    /*
                    In short test mode, don't go by default search.
                    Mods like FAPI have a LOT of versions, and that takes a while to generate manifests for
                     */
                    searchMethod = ModrinthApiCall.SearchMethod.NEWEST.modrinthString,
                    limit = totalCount
                ).execute().body()
                else modrinthApiCall.search(limit = limitPerSearch, offset = offset).execute().body()).also {
                    offset += limitPerSearch
                    attempts = 0
                }
            } catch (_: SocketTimeoutException) {
                logger.warn { "Modrinth search timed out" }
                if (attempts++ > 5) offset += limitPerSearch // Skip the search - too many failed attempts
                continue
            }

            if (search == null) {
                logger.warn { "Modrinth search returned null" }
                continue
            }


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
        if (OperationMode.CREATE !in operationModes) return@flow // Indicated that manifests should not be created
        val createdManifests = createManifests(obtainModrinthInfo().buffer(FLOW_BUFFER))

        // The "original manifest" is still the "latest manifest" as it was just generated
        createdManifests.buffer(FLOW_BUFFER).collect { (thirdPartyApiStatus, potentiallyNullManifest, latestManifest) ->
            emit(
                super.reviewManifest(
                    logger,
                    ThirdPartyApiUsage.MODRINTH_USED,
                    thirdPartyApiStatus,
                    latestManifest,
                    apiDownloader.downloadManifestJson(latestManifest.genericIdentifier),
                    potentiallyNullManifest
                )
            )
        }

    }
}