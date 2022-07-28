package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestWithCreationStatus
import kotlinx.coroutines.flow.Flow
import java.io.IOException

interface NewManifestReviewer {

    /**
     * Reviews newly generated [ManifestJson]s against the original [ManifestJson]s that are already in use,
     * and returns a [ManifestWithCreationStatus] of the corresponding manifests.
     * @throws IOException if an error occurs while obtaining manifests to review.
     * @author ReviversMc
     * @since 1.0.0
     */
    fun reviewManifests(): Flow<ManifestWithCreationStatus>

}