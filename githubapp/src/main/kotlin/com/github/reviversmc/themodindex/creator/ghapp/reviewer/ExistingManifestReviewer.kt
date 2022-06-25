package com.github.reviversmc.themodindex.creator.ghapp.reviewer

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import com.github.reviversmc.themodindex.creator.ghapp.COROUTINES_PER_TASK
import com.github.reviversmc.themodindex.creator.ghapp.data.ReviewStatus
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException

class ExistingManifestReviewer(
    private val apiDownloader: ApiDownloader, private val creator: Creator,
) : ManifestReviewer {

    override suspend fun downloadOriginalManifests() = coroutineScope {
        flow {
            val manifestDownloadSemaphore = Semaphore(COROUTINES_PER_TASK)
            val existingGenericIdentifiers =
                apiDownloader.downloadIndexJson()?.identifiers?.map { it.substringBeforeLast(":") }
                    ?: throw IOException("Could not download manifest index")
            existingGenericIdentifiers.distinct().forEach {
                launch {
                    manifestDownloadSemaphore.withPermit {
                        emit(
                            apiDownloader.downloadManifestJson(it)
                                ?: throw IOException("Could not download manifest $it")
                        )
                    }
                }
            }
        }
    }

    override suspend fun reviewExistingManifests(originalManifests: Flow<ManifestJson>) = coroutineScope {
        flow {
            val manifestUpdateSemaphore = Semaphore(COROUTINES_PER_TASK)

            originalManifests.collect { originalManifest ->
                launch {
                    manifestUpdateSemaphore.withPermit {
                        val createdManifests = originalManifest.modrinthId?.let {
                            creator.createManifestModrinth(
                                it, originalManifest.curseForgeId
                            )
                        } ?: originalManifest.curseForgeId?.let {
                            creator.createManifestCurseForge(
                                it,
                                @Suppress("KotlinConstantConditions") originalManifest.modrinthId
                            )
                        }
                        ?: throw IOException("No modrinth or curseforge id found for manifest ${originalManifest.genericIdentifier}")

                        // Get or default the mod loader.
                        val latestManifest =
                            createdManifests.manifests.firstOrNull { it.genericIdentifier == originalManifest.genericIdentifier }

                        if (latestManifest == null) {
                            val status =
                                if (!ThirdPartyApiUsage.isAllWorking(createdManifests.thirdPartyApiUsage) && ( // First check that api status is not all working
                                            // Then check for individual failure for CF and Modrinth
                                            (originalManifest.curseForgeId != null && ThirdPartyApiUsage.CURSEFORGE_USED !in createdManifests.thirdPartyApiUsage) || (originalManifest.modrinthId != null && ThirdPartyApiUsage.MODRINTH_USED !in createdManifests.thirdPartyApiUsage))
                                ) ReviewStatus.THIRD_PARTY_API_FAILURE // This means that there was some form of api failure
                                else ReviewStatus.MARKED_FOR_REMOVAL

                            emit(ManifestWithCreationStatus(status, null, originalManifest))
                            return@launch
                        }

                        if (latestManifest == originalManifest) {
                            emit(
                                ManifestWithCreationStatus(
                                    ReviewStatus.NO_CHANGE,
                                    latestManifest,
                                    originalManifest
                                )
                            )
                            return@launch
                        }

                        /*
                         Check if only change is one of the following. If it is, then we should trust the more recently created manifest.

                         We should trust the newer version for files, as it has more up-to-date third party api calls
                         We can trust the newer version for author, license, and links,
                         provided that the curse and modrinth id are constant. This means that the change is intended by the mod creator.
                         */
                        if (latestManifest.copy(files = originalManifest.files) == originalManifest ||
                            latestManifest.copy(author = originalManifest.author) == originalManifest ||
                            latestManifest.copy(license = originalManifest.license) == originalManifest ||
                            latestManifest.copy(links = originalManifest.links) == originalManifest
                        ) {
                            emit(
                                ManifestWithCreationStatus(
                                    ReviewStatus.APPROVED_UPDATE,
                                    latestManifest,
                                    originalManifest
                                )
                            )
                            return@launch
                        }

                        /*
                        Names of mods can change, and the identifiers of the manifests will change along with it.
                         */
                        if (latestManifest.copy(
                                genericIdentifier = originalManifest.genericIdentifier,
                                fancyName = originalManifest.fancyName
                            ) == originalManifest
                        ) {
                            // Ensure that the new generic identifier isn't already taken
                            if (latestManifest.genericIdentifier !in (apiDownloader.getOrDownloadIndexJson()?.identifiers
                                    ?: throw IOException("Could not download manifest index"))
                            ) {
                                emit(
                                    ManifestWithCreationStatus(
                                        ReviewStatus.APPROVED_GENERIC_IDENTIFIER_CHANGE,
                                        latestManifest,
                                        originalManifest
                                    )
                                )
                                return@launch
                            }
                        }

                        // Else, make a conflict.
                        emit(
                            ManifestWithCreationStatus(
                                ReviewStatus.MANUAL_REVIEW_REQUIRED,
                                latestManifest,
                                originalManifest
                            )
                        )

                    }
                }
            }
        }
    }
}
