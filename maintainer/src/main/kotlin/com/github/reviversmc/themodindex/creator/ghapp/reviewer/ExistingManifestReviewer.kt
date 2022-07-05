package com.github.reviversmc.themodindex.creator.ghapp.reviewer

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestPendingReview
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import java.io.IOException

interface ExistingManifestReviewer {

    /**
     * Creates manifests for review, using info from [inputChannel], and sends the new [ManifestPendingReview]s to [outputChannel].
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createManifests(inputChannel: ReceiveChannel<ManifestJson>, outputChannel: SendChannel<ManifestPendingReview>)

    /**
     * Reviews newly generated [ManifestJson]s against the original [ManifestJson]s that are already in use,
     * and returns a [ManifestWithCreationStatus] of the corresponding manifests.
     * @throws IOException if an error occurs while obtaining manifests to review.
     * @author ReviversMc
     * @since 1.0.0
     */
    fun reviewManifests(): Flow<ManifestWithCreationStatus>
}