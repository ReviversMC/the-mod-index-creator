package com.github.reviversmc.themodindex.creator.ghapp.reviewer

import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.ghapp.COROUTINES_PER_TASK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mu.KLogger
import java.io.IOException

/**
 * Downloads and returns existing manifests from the manifest repo from [ApiDownloader.formattedBaseUrl], logging events with [logger]
 * @throws IOException if the download fails
 * @author ReviversMC
 * @since 1.0.0
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun ApiDownloader.downloadExistingManifests(logger: KLogger) = coroutineScope {
    produce(capacity = COROUTINES_PER_TASK * 2) {
        val existingGenericIdentifiers = downloadIndexJson()?.identifiers?.map { it.substringBeforeLast(":") }
            ?: throw IOException("Could not download manifest index from $formattedBaseUrl")
        logger.debug { "Downloaded manifest index of repository $formattedBaseUrl" }

        val semaphore = Semaphore(COROUTINES_PER_TASK)
        existingGenericIdentifiers.distinct().forEach {
            launch {
                semaphore.withPermit {
                    send(
                        downloadManifestJson(it) ?: throw IOException("Could not download manifest $it")
                    )
                    logger.debug { "Downloaded manifest $it" }
                }
            }
        }
        close()
    }
}