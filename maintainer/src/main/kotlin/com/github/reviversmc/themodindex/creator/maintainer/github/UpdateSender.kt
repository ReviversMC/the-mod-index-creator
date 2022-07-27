package com.github.reviversmc.themodindex.creator.maintainer.github

import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestWithCreationStatus
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/**
 * A class to manage sending updates to GitHub.
 * @author ReviversMC
 * @since 1.0.0
 */
interface UpdateSender {

    /**
     * Gets the installation token used to authenticate with the GitHub API.
     */
    val gitHubInstallationToken: String

    /**
     * Sends manifests for update to the manifest server.
     * Manifests to update from [manifestFlow] will be reviewed, and prepared for update if they are approved.
     * Should a manifest be marked for manual review, it will be returned as part of this method's [Flow].
     * @throws IOException If any underlying api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @Throws(IOException::class)
    fun sendManifestUpdate(manifestFlow: Flow<ManifestWithCreationStatus>): Flow<ManifestWithCreationStatus>

}