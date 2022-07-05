package com.github.reviversmc.themodindex.creator.ghapp.reviewer

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.data.ManifestWithApiStatus
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import com.github.reviversmc.themodindex.creator.ghapp.COROUTINES_PER_TASK
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestPendingReview
import com.github.reviversmc.themodindex.creator.ghapp.data.ReviewStatus
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class IndexExistingManifestReviewer(
    private val apiDownloader: ApiDownloader, private val creator: Creator, private val testMode: Boolean,
) : ExistingManifestReviewer {

    private val logger = KotlinLogging.logger {}

    // In format of genericIdentifier to manifestWithApiStatus
    private val bufferedManifests = mutableMapOf<String, ManifestWithApiStatus>()

    private fun <K, V> MutableMap<K, V>.getAndRemove(key: K): V? {
        val value = this[key]
        if (value != null) remove(key)
        return value
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun createManifests(
        inputChannel: ReceiveChannel<ManifestJson>,
        outputChannel: SendChannel<ManifestPendingReview>,
    ) {
        val updatedManifestsContext = newSingleThreadContext("updatedExistingManifests")
        val counter = AtomicInteger(0)

        withContext(updatedManifestsContext) {
            repeat(COROUTINES_PER_TASK) {
                launch {
                    if (testMode) {
                        if (counter.getAndIncrement() >= 20) return@launch // Test mode, only process 20 manifests
                    }
                    for (originalManifest in inputChannel) {
                        logger.debug { "Creating manifest for ${originalManifest.genericIdentifier}" }

                        val createdManifests =
                            bufferedManifests.getAndRemove(originalManifest.genericIdentifier) // Try to get a cached value

                                ?: originalManifest.modrinthId?.let {// Else try to create a new one using modrinth id
                                    creator.createManifestModrinth(
                                        it, originalManifest.curseForgeId
                                    )
                                }
                                ?: originalManifest.curseForgeId?.let {// Else try to create a new one using curseforge id
                                    creator.createManifestCurseForge(
                                        it, @Suppress("KotlinConstantConditions") originalManifest.modrinthId
                                    )
                                }
                                ?: throw IOException("No modrinth or curseforge id found for manifest ${originalManifest.genericIdentifier}")

                        // Get or default the mod loader.
                        val latestManifest =
                            // Try to find an exact match for the generic identifier
                            createdManifests.manifests.firstOrNull { it.genericIdentifier == originalManifest.genericIdentifier }
                            // Else, we see if we can at least have a generic identifier with the same loader (indicates that the name of the mod changed)
                                ?: createdManifests.manifests.firstOrNull {
                                    it.genericIdentifier.substringBefore(":") == originalManifest.genericIdentifier.substringBefore(
                                        ":"
                                    )
                                }

                        createdManifests.manifests.forEach {
                            if (it != latestManifest) bufferedManifests[it.genericIdentifier] =
                                createdManifests.copy(manifests = listOf(it)) // Just cache the one manifest that matches the generic identifier.
                        }

                        outputChannel.send(
                            ManifestPendingReview(
                                createdManifests.thirdPartyApiUsage, latestManifest, originalManifest
                            )
                        )
                        logger.debug { "Created manifest for ${originalManifest.genericIdentifier}" }
                    }
                }
            }
        }

        outputChannel.close()
    }


    override fun reviewManifests() = flow {

        val originalManifestChannel = apiDownloader.downloadExistingManifests(logger)
        val updatedManifestChannel = Channel<ManifestPendingReview>(COROUTINES_PER_TASK * 2)
        createManifests(originalManifestChannel, updatedManifestChannel)

        for ((thirdPartyApiUsage, latestManifest, originalManifest) in updatedManifestChannel) {

            if (latestManifest == null) {
                val status =
                    if (!ThirdPartyApiUsage.isAllWorking(thirdPartyApiUsage) && ( // First check that api status is not all working
                                // Then check for individual failure for CF and Modrinth
                                (originalManifest.curseForgeId != null && ThirdPartyApiUsage.CURSEFORGE_USED !in thirdPartyApiUsage) || (originalManifest.modrinthId != null && ThirdPartyApiUsage.MODRINTH_USED !in thirdPartyApiUsage))
                    ) ReviewStatus.THIRD_PARTY_API_FAILURE // This means that there was some form of api failure
                    else ReviewStatus.MARKED_FOR_REMOVAL

                emit(ManifestWithCreationStatus(status, null, originalManifest))
                logger.debug { if (status == ReviewStatus.THIRD_PARTY_API_FAILURE) "Third party api failure for ${originalManifest.genericIdentifier}" else "${originalManifest.genericIdentifier} is marked for removal " }
            } else {

                when {
                    latestManifest == originalManifest -> {
                        emit(
                            ManifestWithCreationStatus(
                                ReviewStatus.NO_CHANGE, latestManifest, originalManifest
                            )
                        )
                        logger.debug { "${latestManifest.genericIdentifier} is unchanged" }
                    }


                    /*
                 Check if only change is one of the following. If it is, then we should trust the more recently created manifest.

                 We should trust the newer version for files, as it has more up-to-date third party api calls
                 We can trust the newer version for author, license, and links,
                 provided that the curse and modrinth id are constant. This means that the change is intended by the mod creator.
                 */
                    latestManifest.copy(files = originalManifest.files) == originalManifest || latestManifest.copy(
                        author = originalManifest.author
                    ) == originalManifest || latestManifest.copy(license = originalManifest.license) == originalManifest || latestManifest.copy(
                        links = originalManifest.links
                    ) == originalManifest -> {
                        emit(
                            ManifestWithCreationStatus(
                                ReviewStatus.APPROVED_UPDATE, latestManifest, originalManifest
                            )
                        )
                        logger.debug { "Update for ${latestManifest.genericIdentifier} is approved" }
                    }

                    /*
                Names of mods can change, and the identifiers of the manifests will change along with it.
                 */
                    latestManifest.copy(
                        genericIdentifier = originalManifest.genericIdentifier, fancyName = originalManifest.fancyName
                    ) == originalManifest -> {
                        // Ensure that the new generic identifier isn't already taken
                        if (latestManifest.genericIdentifier !in (apiDownloader.getOrDownloadIndexJson()?.identifiers
                                ?: throw IOException("Could not download manifest index"))
                        ) {
                            emit(
                                ManifestWithCreationStatus(
                                    ReviewStatus.APPROVED_GENERIC_IDENTIFIER_CHANGE, latestManifest, originalManifest
                                )
                            )
                            logger.debug { "Generic identifier change for ${originalManifest.genericIdentifier} to ${latestManifest.genericIdentifier} is approved" }
                        }
                    }

                    // Else, make a conflict.
                    else -> {
                        emit(
                            ManifestWithCreationStatus(
                                ReviewStatus.UPDATE_CONFLICT, latestManifest, originalManifest
                            )
                        )
                        logger.debug { "Manual review required for ${latestManifest.genericIdentifier}" }
                    }
                }
            }
        }
    }
}