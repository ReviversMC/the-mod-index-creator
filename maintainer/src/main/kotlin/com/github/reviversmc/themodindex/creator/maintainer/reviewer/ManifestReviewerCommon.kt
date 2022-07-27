package com.github.reviversmc.themodindex.creator.maintainer.reviewer

import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import kotlinx.coroutines.flow.flow
import mu.KLogger
import java.io.IOException

/**
 * Downloads and returns existing manifests from the manifest repo from [ApiDownloader.formattedBaseUrl], logging events with [logger]
 * @throws IOException if the download fails
 * @author ReviversMC
 * @since 1.0.0
 */
internal fun ApiDownloader.downloadExistingManifests(logger: KLogger) = flow {
    val existingGenericIdentifiers = downloadIndexJson()?.identifiers?.map { it.substringBeforeLast(":") }
        ?: throw IOException("Could not download manifest index from $formattedBaseUrl")
    logger.debug { "Downloaded manifest index of repository $formattedBaseUrl" }

    existingGenericIdentifiers.distinct().forEach {
        emit(downloadManifestJson(it) ?: throw IOException("Could not download manifest $it"))
        logger.debug { "Downloaded manifest $it" }
    }
}