package com.github.reviversmc.themodindex.creator.maintainer.github

import com.apollographql.apollo3.ApolloClient
import com.github.reviversmc.themodindex.api.data.IndexJson
import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.GHBranch
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.GHRestApp
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.type.FileAddition
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.type.FileDeletion
import com.github.reviversmc.themodindex.creator.maintainer.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.maintainer.data.ReviewStatus
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.rsa.RSASigner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

private typealias GenericIdentifier = String

class GitHubUpdateSender(
    private val apiDownloader: ApiDownloader,
    private val json: Json,
    private val repoOwner: String,
    private val repoName: String,
    private val targetedBranch: String,
    private val gitHubAppId: String,
    private val gitHubRestApp: GHRestApp,
    private val gitHubPrivateKeyPath: String,
) : KoinComponent, UpdateSender {

    private val logger = KotlinLogging.logger {}

    override val gitHubInstallationToken: String
        get() = refreshInstallationTokenAndApi()

    private val ghBranch by inject<GHBranch> {
        parametersOf(
            {get<ApolloClient> { parametersOf(gitHubInstallationToken) }}, repoOwner, repoName
        )
    }

    /*
    We should use the GraphQL API to update the repository (i.e. push, pr, etc.)
    However, the REST API be more useful at times, such as the creation of installation tokens.
    The REST API should be used sparingly, as it is likely that we will hit our REST API rate limit faster than the GraphQL API limit.
     */
    init {
        refreshInstallationTokenAndApi()
    }

    private fun refreshInstallationTokenAndApi(): String {
        val jwtSigner = RSASigner.newSHA256Signer(File(gitHubPrivateKeyPath).readText())
        val jwt = JWT().setIssuedAt(ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(1))
            .setExpiration(ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(10)).setIssuer(gitHubAppId)
        val signedJwt = JWT.getEncoder().encode(jwt, jwtSigner)
        logger.debug { "Signed JWT created." }

        val gitHubInstallationId = gitHubRestApp.installation("Bearer $signedJwt", repoOwner, repoName).execute().body()?.id
            ?: throw IOException("Could not get installation of $repoOwner/$repoName.")
        val installationToken = gitHubRestApp.createAccessToken("Bearer $signedJwt", gitHubInstallationId).execute().body()?.token
            ?: throw IOException("Could not get installation token of $repoOwner/$repoName")
        logger.debug { "GitHub installation token created" }

        return installationToken
    }

    override fun sendManifestUpdate(manifestFlow: Flow<ManifestWithCreationStatus>) = flow {
        logger.debug { "Preparing to send manifest update..." }
        var indexJson = apiDownloader.downloadIndexJson()
            ?: throw IOException("Could not download index.json from ${apiDownloader.formattedBaseUrl}")

        val additions = mutableMapOf<GenericIdentifier, FileAddition>()
        val deletions = mutableListOf<FileDeletion>()

        manifestFlow.collect { (reviewStatus, latestManifest, originalManifest) ->
            when (reviewStatus) {
                ReviewStatus.APPROVED_GENERIC_IDENTIFIER_CHANGE, ReviewStatus.APPROVED_UPDATE -> {
                    if (latestManifest == null) {
                        logger.error { "Latest manifest is null, but review status is $reviewStatus." }
                        return@collect
                    }

                    if (reviewStatus == ReviewStatus.APPROVED_GENERIC_IDENTIFIER_CHANGE) {
                        indexJson = indexJson.removeFromIndex(originalManifest)
                        deletions.add(
                            FileDeletion(
                                "mods/${originalManifest.genericIdentifier.replaceFirst(':', '/')}.json"
                            )
                        )
                        logger.debug { "To remove ${originalManifest.genericIdentifier}, in favour of ${latestManifest.genericIdentifier}" }
                    }

                    if (additions.containsKey(latestManifest.genericIdentifier)) {
                        logger.warn { "Duplicate manifest found for ${latestManifest.genericIdentifier}" }
                        val conflictManifest = json.decodeFromString<ManifestJson>(
                            Base64.getDecoder()
                                .decode(additions[latestManifest.genericIdentifier]!!.contents as String)
                                .decodeToString()
                        )
                        indexJson = indexJson.removeFromIndex(conflictManifest)
                        additions.remove(latestManifest.genericIdentifier)

                        emit(
                            ManifestWithCreationStatus(
                                ReviewStatus.UPDATE_CONFLICT,
                                latestManifest,
                                conflictManifest
                            )
                        )
                    } else {
                        additions[latestManifest.genericIdentifier] = FileAddition(
                            "mods/${latestManifest.genericIdentifier.replaceFirst(':', '/')}.json",
                            json.encodeToString(latestManifest).toBase64WithNewline(),
                        )

                        indexJson = indexJson.addToIndex(latestManifest)
                        logger.debug { "Added ${latestManifest.genericIdentifier} to update" }
                    }
                }


                ReviewStatus.MARKED_FOR_REMOVAL -> {
                    indexJson = indexJson.removeFromIndex(originalManifest)
                    deletions.add(
                        FileDeletion(
                            "mods/${originalManifest.genericIdentifier.replaceFirst(':', '/')}.json"
                        )
                    )
                    logger.debug { "To remove ${originalManifest.genericIdentifier}" }
                }

                ReviewStatus.CREATION_CONFLICT, ReviewStatus.UPDATE_CONFLICT -> {
                    logger.debug { "Manual review required for ${originalManifest.genericIdentifier}. Emitting manifest for handling." }
                    emit(ManifestWithCreationStatus(reviewStatus, latestManifest, originalManifest))
                }

                ReviewStatus.NO_CHANGE -> logger.debug { "No change for ${latestManifest?.genericIdentifier ?: originalManifest.genericIdentifier}" }
                ReviewStatus.THIRD_PARTY_API_FAILURE -> logger.debug { "Third party API failure for ${latestManifest?.genericIdentifier ?: originalManifest.genericIdentifier}" }

            }
        }

        if (apiDownloader.downloadIndexJson() == indexJson) {
            logger.debug { "No changes to index.json, no push to repository required." }
            return@flow
        }
        indexJson = indexJson.copy(identifiers = indexJson.identifiers.sorted())

        refreshInstallationTokenAndApi()

        if (!ghBranch.doesRefExist(targetedBranch)) {
            ghBranch.createRef(
                ghBranch.defaultBranchRef(), targetedBranch
            )
        }

        ghBranch.commitAndUpdateRef(
            targetedBranch,
            "Automated manifest update: UTC ${ZonedDateTime.now(ZoneOffset.UTC)}",
            additions.values + FileAddition("mods/index.json", json.encodeToString(indexJson).toBase64WithNewline()),
            deletions
        )

        logger.debug { "Pushed manifest updates to branch $targetedBranch." }

    }

    /**
     * Converts a [String] into a Base64 string.
     */
    private fun String.toBase64WithNewline() =
        Base64.getEncoder().encodeToString((this + if (this.endsWith("\n")) "" else "\n").toByteArray())

    /**
     * Adds [ManifestJson] entries to the [IndexJson] if they are not already present.
     * Returns the [IndexJson] with the new entries added, or the same index if no new entries were added.
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun IndexJson.addToIndex(manifest: ManifestJson): IndexJson =
        copy(identifiers = identifiers.toMutableList().apply {
            manifest.files.forEach {
                val identifier = "${manifest.genericIdentifier}:${it.shortSha512Hash}"
                if (identifier !in this) this.add(identifier)
            }
        }.toList())

    /**
     * Removes [ManifestJson] entries from the [IndexJson].
     * Returns the [IndexJson] with the removed entries removed, or the same index if no entries were removed.
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun IndexJson.removeFromIndex(manifest: ManifestJson): IndexJson =
        copy(identifiers = identifiers.toMutableList().apply {
            manifest.files.forEach { remove("${manifest.genericIdentifier}:${it.shortSha512Hash}") }
        }.toList())
}
