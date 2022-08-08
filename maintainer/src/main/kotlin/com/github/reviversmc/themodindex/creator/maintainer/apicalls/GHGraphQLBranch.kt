package com.github.reviversmc.themodindex.creator.maintainer.apicalls

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Optional
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.type.FileAddition
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.type.FileDeletion
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import kotlin.concurrent.timer

class GHGraphQLBranch(
    private val refreshApolloClient: () -> ApolloClient,
    private val repoOwner: String,
    private val repoName: String,
) : GHBranch {

    private val logger = KotlinLogging.logger {}

    private var apolloClient = refreshApolloClient()

    init {
        // Refresh the client every 50 minutes, so that we don't get an use an expired token
        timer("GHGraphQLBranchRefresh", true, 50L * 60L * 1000L, 50L * 60L * 1000L) {
            apolloClient = refreshApolloClient()
            logger.info { "Refreshed apollo client for GH Branch" }
        }
    }

    override suspend fun defaultBranchRef(): String {
        val query = apolloClient.query(DefaultBranchRefQuery(repoOwner, repoName)).execute()
        logger.debug { "Executed default ref query for $repoOwner/$repoName" }

        if (query.hasErrors()) {
            logger.error { "Error getting default branch ref: ${query.errors}" }
            query.errors!!.forEach { logger.error { it.toString() } }
            throw IllegalStateException("Error getting default branch ref")
        }

        return query.data?.repository?.defaultBranchRef?.name
            ?: throw IllegalStateException("Error getting default branch ref")
    }

    override suspend fun doesRefExist(branchName: String): Boolean {
        val query = apolloClient.query(GetRefsQuery(repoOwner, repoName, branchName)).execute()
        logger.debug { "Executed query to check if branch $branchName exists in $repoOwner/$repoName" }

        if (query.hasErrors()) {
            logger.error { "Error while checking if ref exists: ${query.errors}" }
            query.errors!!.forEach { logger.error { it.toString() } }
            throw IllegalStateException("Error while checking if ref exists")
        }

        return query.data?.repository?.refs?.edges?.map { it?.node?.name }?.contains(branchName) == true
    }

    override suspend fun createRef(branchFrom: String, branchCreated: String) {
        val query = apolloClient.query(RepoIdAndBranchHeadOidQuery(repoOwner, repoName, branchFrom)).execute()
        logger.debug { "Executed query to obtain the repo id of $repoOwner/$repoName and the head object id of branch $branchFrom" }

        if (query.hasErrors()) {
            logger.error { "Error getting ref $branchFrom" }
            query.errors!!.forEach { logger.error { it.message } }
            throw IllegalStateException("Error getting ref $branchFrom")
        }

        val mutation = apolloClient.mutation(
            CreateBranchMutation(
                if (!branchFrom.startsWith("refs/heads/")) "refs/heads/$branchCreated"
                else branchFrom,
                query.data?.repository?.ref?.target?.oid ?: throw IllegalStateException("No oid found for $branchFrom"),
                query.data?.repository?.id ?: throw IllegalStateException("No repo id found for $branchFrom")
            )
        ).execute()
        logger.debug { "Executed mutation to create branch $branchCreated from $branchFrom" }

        if (mutation.hasErrors()) {
            logger.error { "Error creating ref $branchFrom" }
            mutation.errors!!.forEach { logger.error { it.message } }
            throw IllegalStateException("Error creating ref $branchFrom")
        }
    }

    override suspend fun commitAndUpdateRef(
        branchName: String,
        message: String,
        additions: List<FileAddition>,
        deletions: List<FileDeletion>,
    ) {
        if (additions.isEmpty() && deletions.isEmpty()) return // No need to make call if no changes
        logger.debug { "No changes to commit were provided" }

        val query = apolloClient.query(BranchHeadOidQuery(repoOwner, repoName, "refs/heads/$branchName")).execute()
        logger.debug { "Executed query to obtain the head object id of branch $branchName in repo $repoOwner/$repoName" }

        if (query.hasErrors()) {
            logger.error("Error getting branch head oid")
            query.errors!!.forEach { logger.error(it.toString()) }
            throw IllegalStateException("Error getting branch head oid")
        }

        val headOid = query.data?.repository?.ref?.target?.oid
            ?: throw IllegalStateException("No oid for head commit of branch $branchName in $repoOwner/$repoName found")

        val mutation = apolloClient.mutation(
            UpdateBranchMutation(
                "$repoOwner/$repoName", branchName, message, headOid, deletions, additions
            )
        ).execute()
        logger.debug { "Executed mutation to update branch $branchName in repo $repoOwner/$repoName" }

        if (mutation.hasErrors()) {
            logger.error { "Error updating branch $branchName in $repoOwner/$repoName" }
            mutation.errors!!.forEach { logger.error { it.message } }
            throw IllegalStateException("Error updating branch $branchName in $repoOwner/$repoName")
        }
    }

    private suspend fun obtainRepoIdQuery(): ApolloResponse<RepoIdQuery.Data> {
        val query = apolloClient.query(RepoIdQuery(repoOwner, repoName)).execute()
        logger.debug { "Executed query to obtain the repo id of $repoOwner/$repoName" }

        if (query.hasErrors()) {
            logger.error { "Error getting repo id" }
            query.errors!!.forEach { logger.error { it.message } }
            throw IllegalStateException("Error getting repo id")
        }

        return query
    }

    override suspend fun createPullRequest(
        prFromBranch: String,
        prToBranch: String,
        prTitle: String,
        prMessage: String?,
    ) {
        val query = obtainRepoIdQuery()

        val mutation = apolloClient.mutation(
            CreatePRMutation(
                query.data?.repository?.id ?: throw IllegalStateException("No repo id found for $repoOwner/$repoName"),
                prToBranch,
                prFromBranch,
                prTitle,
                Optional.presentIfNotNull(prMessage)
            )
        ).execute()
        logger.debug { "Executed mutation to create pull request from $prFromBranch to $prToBranch" }

        if (mutation.hasErrors()) {
            logger.error { "Error creating pull request" }
            mutation.errors!!.forEach { logger.error { it.message } }
            throw IllegalStateException("Error creating pull request")
        }
    }

    override suspend fun downloadBranchTarGZ(branchName: String, okHttpClient: OkHttpClient): InputStream {
        val query = apolloClient.query(DownloadBranchTarGZQuery(repoOwner, repoName, branchName)).execute()
        logger.debug { "Executed query to download tar.gz of branch $branchName in $repoOwner/$repoName" }

        if (query.hasErrors()) {
            logger.error { "Error downloading tar.gz of branch $branchName in $repoOwner/$repoName" }
            query.errors!!.forEach { logger.error { it.message } }
            throw IllegalStateException("Error downloading tar.gz of branch $branchName in $repoOwner/$repoName")
        }

        return okHttpClient.newCall(
            Request.Builder().url(
                    query.data?.repository?.ref?.target?.onCommit?.tarballUrl?.toString()
                        ?: throw IllegalStateException("No tarball url found for $branchName in $repoOwner/$repoName")
                ).build()
        ).execute().body?.byteStream()
            ?: throw IllegalStateException("No tar.gz found for branch $branchName in $repoOwner/$repoName")
    }

    override suspend fun mergeBranchWithoutPR(
        mergedIntoName: String,
        mergedFromBranchName: String,
        commitMessage: String,
    ) {
        val query = obtainRepoIdQuery()

        val mutation = apolloClient.mutation(
            MergeBranchWithoutPRMutation(
                mergedIntoName,
                mergedFromBranchName,
                commitMessage,
                query.data?.repository?.id ?: throw IllegalStateException("No repo id found for $repoOwner/$repoName"),
            )
        ).execute()
        logger.debug { "Executed mutation to merge branch $mergedFromBranchName into $mergedIntoName" }

        if (mutation.hasErrors()) {
            logger.error { "Error merging branch without PR" }
            mutation.errors!!.forEach { logger.error { it.message } }
            throw IllegalStateException("Error merging branch without PR")
        }
    }
}