package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestPendingReview
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestWithCreationStatus
import kotlinx.coroutines.flow.Flow
import java.io.IOException

interface ExistingManifestReviewer {

    /**
     * Creates manifests for review, using info from [originalManifests], and emits the new [ManifestPendingReview]s
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createManifests(originalManifests: Flow<ManifestJson>): Flow<ManifestPendingReview>

    /**
     * Reviews newly generated [ManifestJson]s against the original [ManifestJson]s that are already in use,
     * and returns a [ManifestWithCreationStatus] of the corresponding manifests.
     * @throws IOException if an error occurs while obtaining manifests to review.
     * @author ReviversMc
     * @since 1.0.0
     */
    fun reviewManifests(): Flow<ManifestWithCreationStatus>
}