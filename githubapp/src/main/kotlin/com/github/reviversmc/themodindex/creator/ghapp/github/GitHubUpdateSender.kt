package com.github.reviversmc.themodindex.creator.ghapp.github

import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.Creator

import com.github.reviversmc.themodindex.creator.ghapp.data.AppConfig
import com.github.reviversmc.themodindex.creator.ghapp.data.ManifestWithCreationStatus
import com.github.reviversmc.themodindex.creator.ghapp.data.ReviewStatus
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.rsa.RSASigner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import java.io.File
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime

class GitHubUpdateSender(
    private val apiDownloader: ApiDownloader,
    private val creator: Creator,
    config: AppConfig,
    private val json: Json,
) : KoinComponent, UpdateSender {

    private val logger = KotlinLogging.logger {}

    val branchName: String
    private val gitHubInstallationApi: GitHub
    private val gitHubUserRepo = "${config.targetedGitHubRepoOwner}/${config.targetedGitHubRepoName}"
    private val gitHubRepo: GHRepository

    init {
        val jwtSigner = RSASigner.newSHA256Signer(File(config.gitHubPrivateKeyPath).readText())
        val jwt = JWT().setIssuedAt(ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(1))
            .setExpiration(ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(10)).setIssuer(config.gitHubAppId)
        val signedJwt = JWT.getEncoder().encode(jwt, jwtSigner)
        logger.debug { "Signed JWT created." }


        val gitHubAppApi = get<GitHub>(named("jwt")) { parametersOf(signedJwt) }
        val gitHubInstallationToken = gitHubAppApi.app.getInstallationByRepository(
            config.targetedGitHubRepoOwner, config.targetedGitHubRepoName
        ).createToken().create().token
        logger.debug { "GitHub installation token created: $gitHubInstallationToken" }

        gitHubInstallationApi = get(named("installation")) { parametersOf(gitHubInstallationToken) }
        gitHubRepo = gitHubInstallationApi.getRepository(gitHubUserRepo)
        logger.debug { "Obtained repository \"${gitHubRepo.owner.name}/${gitHubRepo.name}\"" }

        branchName = gitHubRepo.defaultBranch
        if (branchName !in gitHubRepo.branches) {
            gitHubRepo.createRef( // Creates branch
                "refs/heads/$branchName", gitHubRepo.getBranch(gitHubRepo.defaultBranch).shA1
            )

            logger.info { "Created branch $branchName." }
        } else logger.info { "Branch $branchName already exists, and will be used." }

    }

    override fun sendManifestUpdate(manifestFlow: Flow<ManifestWithCreationStatus>) = flow {
        // TODO Find a way to remove files from a tree. Our current solution doesn't allow for branches or removal via a tree.
        var indexJson = apiDownloader.downloadIndexJson() ?: throw IOException("Could not download index.json")
        val updateTree = gitHubRepo.createTree()

        val genericIdentifiersToRemove = mutableListOf<String>()
        val genericIdentifiersToReplace = mutableMapOf<String, String>()

        manifestFlow.collect { (reviewStatus, latestManifest, originalManifest) ->
            when (reviewStatus) {
                ReviewStatus.APPROVED_GENERIC_IDENTIFIER_CHANGE, ReviewStatus.APPROVED_UPDATE -> {
                    if (latestManifest == null) {
                        logger.error { "Latest manifest is null, but review status is $reviewStatus." }
                        return@collect
                    }

                    if (reviewStatus == ReviewStatus.APPROVED_GENERIC_IDENTIFIER_CHANGE) {
                        indexJson = creator.removeFromIndex(indexJson, originalManifest)
                        genericIdentifiersToReplace[originalManifest.genericIdentifier] =
                            latestManifest.genericIdentifier
                        logger.info { "To remove ${originalManifest.genericIdentifier}, in favour of ${latestManifest.genericIdentifier}" }
                    }

                    updateTree.add(
                        "mods/${latestManifest.genericIdentifier.replaceFirst(':', '/')}.json",
                        json.encodeToString(latestManifest),
                        false
                    )
                    indexJson = creator.addToIndex(indexJson, latestManifest)
                    logger.info { "Added ${latestManifest.genericIdentifier} to update" }
                }

                ReviewStatus.MARKED_FOR_REMOVAL -> {
                    indexJson = creator.removeFromIndex(indexJson, originalManifest)
                    genericIdentifiersToRemove.add(originalManifest.genericIdentifier)
                    logger.info { "To remove ${originalManifest.genericIdentifier}" }
                }

                ReviewStatus.MANUAL_REVIEW_REQUIRED -> {
                    logger.info { "Manual review required for ${originalManifest.genericIdentifier}. Emitting manifest for handling." }
                    emit(ManifestWithCreationStatus(reviewStatus, latestManifest, originalManifest))
                }

                ReviewStatus.NO_CHANGE -> logger.debug { "No change for ${latestManifest?.genericIdentifier ?: originalManifest.genericIdentifier}" }
                ReviewStatus.THIRD_PARTY_API_FAILURE -> logger.error { "Third party API failure for ${latestManifest?.genericIdentifier ?: originalManifest.genericIdentifier}" }

            }
        }

        val commit =
            gitHubRepo.createCommit().tree(updateTree.baseTree(gitHubRepo.getBranch(branchName).shA1).create().sha)
                .parent(gitHubRepo.getBranch(branchName).shA1)
                .message("Automated manifest update: UTC ${ZonedDateTime.now(ZoneOffset.UTC)}")
                .create()
        gitHubRepo.getRef("refs/heads/$branchName").updateTo(commit.shA1)
        logger.info { "Commit created and sent." }

        genericIdentifiersToRemove.forEach {
            val deleteCommit = gitHubRepo.getFileContent("mods/${it.replaceFirst(':', '/')}.json")
                .delete("Manifest $it removed").commit
            gitHubRepo.getRef("refs/heads/$branchName").updateTo(deleteCommit.shA1)
        }

        genericIdentifiersToReplace.forEach { (oldGenericIdentifier, newGenericIdentifier) ->
            val replaceCommit = gitHubRepo.getFileContent("mods/${oldGenericIdentifier.replaceFirst(':', '/')}.json")
                .delete("Manifest $oldGenericIdentifier replaced with $newGenericIdentifier").commit
            gitHubRepo.getRef("refs/heads/$branchName").updateTo(replaceCommit.shA1)
        }

        logger.info { "Manifest update completed." }

    }
}
