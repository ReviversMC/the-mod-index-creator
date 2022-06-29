package com.github.reviversmc.themodindex.creator.ghapp.reviewer

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import kotlinx.coroutines.flow.Flow

interface ManifestReviewer {

    /**
     * Downloads and emits all manifests from a repository specified by the [ApiDownloader].
     * @author ReviversMC
     * @since 1.0.0
     */
    fun downloadOriginalManifests(): Flow<ManifestJson>

    /**
     * Reviews newly generated [ManifestJson]s against the original [ManifestJson]s from [originalManifests] that are already in use,
     * and returns a [ManifestWithCreationStatus] of the corresponding manifests.
     * [downloadOriginalManifests] can be used to provide the [originalManifests].
     * @author ReviversMc
     * @since 1.0.0
     */
    fun reviewExistingManifests(originalManifests: Flow<ManifestJson>): Flow<ManifestWithCreationStatus>
}