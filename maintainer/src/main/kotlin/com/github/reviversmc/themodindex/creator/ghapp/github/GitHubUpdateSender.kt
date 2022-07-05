package com.github.reviversmc.themodindex.creator.ghapp.github

import com.github.reviversmc.themodindex.api.data.IndexJson
import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.ghapp.apicalls.GHBranch
import com.github.reviversmc.themodindex.creator.ghapp.apicalls.type.FileAddition
import com.github.reviversmc.themodindex.creator.ghapp.apicalls.type.FileDeletion
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.ghapp.data.ReviewStatus
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.rsa.RSASigner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.kohsuke.github.GitHub
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

class GitHubUpdateSender(
    private val apiDownloader: ApiDownloader,
    private val json: Json,
    private val repoOwner: String,
    private val repoName: String,
    private val targetedBranch: String,
    private val gitHubAppId: String,
    private val gitHubPrivateKeyPath: String,
) : KoinComponent, UpdateSender {

    private val logger = KotlinLogging.logger {}

    override val gitHubInstallationToken: String
        get() = refreshInstallationTokenAndApi()

    private val ghBranch by inject<GHBranch> {
        parametersOf(
            gitHubInstallationToken, repoOwner, repoName
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


        val gitHubAppApi = get<GitHub> { parametersOf(signedJwt) }
        val installationToken = gitHubAppApi.app.getInstallationByRepository(
            repoOwner, repoName
        ).createToken().create().token
        logger.debug { "GitHub installation token created: $installationToken" }

        return installationToken
    }

    override fun sendManifestUpdate(manifestFlow: Flow<ManifestWithCreationStatus>) = flow {
        logger.debug { "Preparing to send manifest update..." }
        var indexJson = apiDownloader.downloadIndexJson() ?: throw IOException("Could not download index.json")

        val additions = mutableListOf<FileAddition>()
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

                    additions.add(
                        FileAddition(
                            "mods/${latestManifest.genericIdentifier.replaceFirst(':', '/')}.json",
                            json.encodeToString(latestManifest).toBase64WithNewline(),
                        )
                    )
                    indexJson = indexJson.addToIndex(latestManifest)
                    logger.debug { "Added ${latestManifest.genericIdentifier} to update" }
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

        if (!get<GitHub> { parametersOf(gitHubInstallationToken) }.isCredentialValid) refreshInstallationTokenAndApi()

        additions.add(FileAddition("mods/index.json", json.encodeToString(indexJson).toBase64WithNewline()))

        if (!ghBranch.doesRefExist(targetedBranch)) {
            ghBranch.createRef(
                ghBranch.defaultBranchRef(), targetedBranch
            )
        }

        ghBranch.commitAndUpdateRef(
            targetedBranch, "Automated manifest update: UTC ${ZonedDateTime.now(ZoneOffset.UTC)}", additions, deletions
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
                val identifier = "${manifest.genericIdentifier}:${it.sha512Hash}"
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
            manifest.files.forEach { remove("${manifest.genericIdentifier}:${it.sha512Hash}") }
        }.toList())
}
