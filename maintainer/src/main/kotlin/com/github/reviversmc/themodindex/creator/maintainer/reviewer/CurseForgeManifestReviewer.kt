package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.apicalls.CurseForgeApiCall
import com.github.reviversmc.themodindex.creator.core.apicalls.CurseModData
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

class CurseForgeManifestReviewer(
    private val apiDownloader: ApiDownloader,
    private val creator: Creator,
    private val curseForgeApiCall: CurseForgeApiCall,
    private val curseForgeApiKey: String,
    private val existingManifests: List<ManifestJson>,
    private val runMode: RunMode,
    private val operationModes: List<OperationMode>,
) : NewManifestReviewer() {

    private val logger = KotlinLogging.logger {}

    private suspend fun obtainCurseForgeInfo() = flow {
        logger.debug { "Obtaining CurseForge info..." }

        val existingCurseIds = mutableListOf<Int>().apply {
            existingManifests.forEach { existingManifest ->
                existingManifest.curseForgeId?.let { add(it) }
            }
            logger.debug { "Downloaded existing manifests" }
        }.toList()


        val firstSearch = curseForgeApiCall.search(curseForgeApiKey, 0).execute().body()
            ?: throw IOException("No response from CurseForge")
        logger.debug { "Made first search to CurseForge" }
        val limitPerSearch = firstSearch.pagination.pageSize

        val totalCount = if (runMode == RunMode.TEST_SHORT) 20 // Just test with a small number of results
        else firstSearch.pagination.totalCount // Otherwise, use the total count from the first search

        logger.debug { "Total of $totalCount CurseForge mods found" }

        var offset = limitPerSearch
        // Use the data from the first search, since we already have it
        firstSearch.data.forEach {
            if (it.id !in existingCurseIds) {
                emit(it) // emit the response, not the id
                logger.debug { "Found new CurseForge project ${it.id}" }
            }
        }

        var attempts = 0
        while (offset < totalCount) {

            val search = try {
                (if (runMode == RunMode.TEST_SHORT) curseForgeApiCall.search(curseForgeApiKey, offset, totalCount)
                    .execute().body()
                else curseForgeApiCall.search(curseForgeApiKey, offset).execute().body()).also {
                    offset += limitPerSearch
                    attempts = 0
                }
            } catch (_: SocketTimeoutException) {
                logger.warn { "CurseForge search timed out" }
                if (attempts++ > 5) offset += limitPerSearch // Skip the search - too many failed attempts
                continue
            }

            if (search == null) {
                logger.warn { "CurseForge search returned null" }
                continue
            }


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
        inputFlow.buffer(FLOW_BUFFER).collect { curseForgeMod ->
            logger.debug { "($counter) Creating manifest for CurseForge project ${curseForgeMod.id}" }

            val createdManifests = try {
                creator.createManifestCurseForge(curseForgeMod)
            } catch (_: SocketTimeoutException) {
                logger.warn { "($counter) Socket timeout while creating manifest for Modrinth project ${curseForgeMod.id}" }
                null
            } catch (ex: HttpException) {
                logger.warn { "($counter) HTTP exception while creating manifest for Modrinth project ${curseForgeMod.id}: code ${ex.code()}" }
                null
            } ?: run {
                ++counter
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
        if (OperationMode.CREATE !in operationModes) return@flow // Indicated that manifests should not be created
        val createdManifests = createManifests(obtainCurseForgeInfo())

        // The "original manifest" is still the "latest manifest" as it was just generated
        createdManifests.buffer(FLOW_BUFFER).collect { (thirdPartyApiStatus, potentiallyNullManifest, latestManifest) ->
            emit(
                super.reviewManifest(
                    logger,
                    ThirdPartyApiUsage.CURSEFORGE_USED,
                    thirdPartyApiStatus,
                    latestManifest,
                    apiDownloader.downloadManifestJson(latestManifest.genericIdentifier),
                    potentiallyNullManifest
                )
            )
        }
    }
}