package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.data.ManifestLinks
import com.github.reviversmc.themodindex.api.data.RelationsToOtherMods
import com.github.reviversmc.themodindex.api.data.VersionFile
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.maintainer.data.ReviewStatus
import kotlinx.coroutines.flow.Flow
import mu.KLogger
import java.io.IOException

abstract class NewManifestReviewer {

    /**
     * Reviews newly generated [ManifestJson]s against the original [ManifestJson]s that are already in use,
     * and returns a [ManifestWithCreationStatus] of the corresponding manifests.
     * @throws IOException if an error occurs while obtaining manifests to review.
     * @author ReviversMc
     * @since 1.0.0
     */
    abstract fun reviewManifests(): Flow<ManifestWithCreationStatus>

    /**
     * Review a single generated [latestManifest] against an [existingManifestInRepo], if available, and runs a merger strategy on the two manifests.
     * Returns a [ManifestWithCreationStatus] that is either approved, merged, or rejected.
     * @throws IOException if an error occurs while obtaining the existing manifest in the repo.
     * @author ReviversMc
     * @since 1.0.0
     */
    protected fun reviewManifest(
        logger: KLogger,
        requiredThirdPartyApi: ThirdPartyApiUsage,
        thirdPartyApiUsage: List<ThirdPartyApiUsage>,
        latestManifest: ManifestJson,
        existingManifestInRepo: ManifestJson?,
        potentiallyNullManifest: ManifestJson? = null,
    ): ManifestWithCreationStatus {
        if (requiredThirdPartyApi !in thirdPartyApiUsage) {
            logger.debug { "Third party api failure for ${latestManifest.genericIdentifier}" }
            return ManifestWithCreationStatus(
                ReviewStatus.THIRD_PARTY_API_FAILURE, potentiallyNullManifest, latestManifest
            )

        } else {
            if (existingManifestInRepo == null) { // Means that this is the first copy
                logger.debug { "Creation of ${latestManifest.genericIdentifier} is approved" }
                return ManifestWithCreationStatus(ReviewStatus.APPROVED_UPDATE, potentiallyNullManifest, latestManifest)

            } else if (
            // Check if at least one of them is missing modrinth/curse id
                latestManifest.modrinthId == null && existingManifestInRepo.modrinthId != null ||
                latestManifest.modrinthId != null && existingManifestInRepo.modrinthId == null ||
                latestManifest.curseForgeId == null && existingManifestInRepo.curseForgeId != null ||
                latestManifest.curseForgeId != null && existingManifestInRepo.curseForgeId == null
            ) {


                latestManifest.files.map { it.shortSha512Hash }.forEach { latestGeneratedManifestHash ->
                    if (existingManifestInRepo.files.map { it.shortSha512Hash }
                            .contains(latestGeneratedManifestHash)) {

                        logger.debug { "Merged ${latestManifest.genericIdentifier} with an existing manifest in repo" }

                        return ManifestWithCreationStatus(
                            ReviewStatus.APPROVED_UPDATE,
                            ManifestJson(
                                latestManifest.indexVersion,
                                latestManifest.genericIdentifier,
                                latestManifest.fancyName,
                                latestManifest.author,
                                latestManifest.license ?: existingManifestInRepo.license,
                                latestManifest.curseForgeId ?: existingManifestInRepo.curseForgeId,
                                latestManifest.modrinthId ?: existingManifestInRepo.modrinthId,
                                ManifestLinks(
                                    latestManifest.links.issue ?: existingManifestInRepo.links.issue,
                                    latestManifest.links.sourceControl
                                        ?: existingManifestInRepo.links.sourceControl,
                                    latestManifest.links.others + existingManifestInRepo.links.others
                                ),

                                latestManifest.files.map { versionFile ->
                                    VersionFile(
                                        versionFile.fileName,
                                        versionFile.mcVersions + (existingManifestInRepo.files.firstOrNull { versionFile.shortSha512Hash == it.shortSha512Hash }?.mcVersions
                                            ?: emptyList()),
                                        versionFile.shortSha512Hash,
                                        versionFile.downloadUrls + (existingManifestInRepo.files.firstOrNull { versionFile.shortSha512Hash == it.shortSha512Hash }?.downloadUrls
                                            ?: emptyList()),
                                        versionFile.curseDownloadAvailable || existingManifestInRepo.files.firstOrNull { versionFile.shortSha512Hash == it.shortSha512Hash }?.curseDownloadAvailable == true,
                                        RelationsToOtherMods(
                                            (versionFile.relationsToOtherMods.required + (existingManifestInRepo.files.firstOrNull { versionFile.shortSha512Hash == it.shortSha512Hash }?.relationsToOtherMods?.required
                                                ?: emptyList())).distinct(),
                                            (versionFile.relationsToOtherMods.incompatible + (existingManifestInRepo.files.firstOrNull { versionFile.shortSha512Hash == it.shortSha512Hash }?.relationsToOtherMods?.incompatible
                                                ?: emptyList())).distinct()
                                        )
                                    )
                                } +
                                        // Find all files that were not in the latest manifest, and add them. No configuration required, as there is nothing to compare to
                                        existingManifestInRepo.files.filter { versionFile ->
                                            versionFile.shortSha512Hash !in latestManifest.files.map { it.shortSha512Hash }
                                        }
                            ),
                            existingManifestInRepo
                        )
                    }
                }
            }

            logger.warn { "Conflict detected: Created ${existingManifestInRepo.genericIdentifier} instead of updating an existing version" }

            return ManifestWithCreationStatus(
                ReviewStatus.UPDATE_CONFLICT,
                latestManifest,
                existingManifestInRepo
            )

        }

    }

}