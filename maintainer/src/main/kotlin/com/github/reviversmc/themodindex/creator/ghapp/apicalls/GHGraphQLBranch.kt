package com.github.reviversmc.themodindex.creator.ghapp.apicalls

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.github.reviversmc.themodindex.creator.ghapp.apicalls.type.FileAddition
import com.github.reviversmc.themodindex.creator.ghapp.apicalls.type.FileDeletion
import mu.KotlinLogging

class GHGraphQLBranch(
    private val apolloClient: ApolloClient,
    private val repoOwner: String,
    private val repoName: String,
) : GHBranch {

    private val logger = KotlinLogging.logger {}

    override suspend fun defaultBranchRef(): String {
        val query = apolloClient.query(DefaultBranchRefQuery(repoOwner, repoName)).execute()

        if (query.hasErrors()) {
            logger.error { "Error getting default branch ref: ${query.errors}" }
            query.errors!!.forEach { logger.error { it.toString() } }
            throw IllegalStateException("Error getting default branch ref")
        }

        return query.data?.repository?.defaultBranchRef?.name ?: throw IllegalStateException("Error getting default branch ref")
    }

    override suspend fun doesRefExist(branchName: String): Boolean {
        val query = apolloClient.query(GetRefsQuery(repoOwner, repoName, branchName)).execute()

        if (query.hasErrors()) {
            logger.error { "Error while checking if ref exists: ${query.errors}" }
            query.errors!!.forEach { logger.error { it.toString() } }
            throw Exception("Error while checking if ref exists")
        }
        logger.debug { query.data?.repository?.refs?.edges?.map { it?.node?.name } }
        return query.data?.repository?.refs?.edges?.map { it?.node?.name }?.contains(branchName) == true
    }

    override suspend fun createRef(branchFrom: String, branchCreated: String) {
        val query = apolloClient.query(RepoIdAndBranchHeadOidQuery(repoOwner, repoName, branchFrom)).execute()

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

        val query = apolloClient.query(BranchHeadOidQuery(repoOwner, repoName, "refs/heads/$branchName")).execute()

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

        if (mutation.hasErrors()) {
            logger.error { "Error updating branch $branchName in $repoOwner/$repoName" }
            mutation.errors!!.forEach { logger.error { it.message } }
            throw IllegalStateException("Error updating branch $branchName in $repoOwner/$repoName")
        }
    }

    override suspend fun createPullRequest(
        prFromBranch: String,
        prToBranch: String,
        prTitle: String,
        prMessage: String?,
    ) {
        val query = apolloClient.query(RepoIdQuery(repoOwner, repoName)).execute()

        if (query.hasErrors()) {
            logger.error { "Error getting repo id" }
            query.errors!!.forEach { logger.error { it.message } }
            throw IllegalStateException("Error getting repo id")
        }

        val mutation = apolloClient.mutation(
            CreatePRMutation(
                query.data?.repository?.id ?: throw IllegalStateException("No repo id found for $repoOwner/$repoName"),
                prToBranch,
                prFromBranch,
                prTitle,
                Optional.presentIfNotNull(prMessage)
            )
        ).execute()

        if (mutation.hasErrors()) {
            logger.error { "Error creating pull request" }
            mutation.errors!!.forEach { logger.error { it.message } }
            throw IllegalStateException("Error creating pull request")
        }
    }
}