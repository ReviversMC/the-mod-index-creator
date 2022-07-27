package com.github.reviversmc.themodindex.creator.maintainer.apicalls

import com.github.reviversmc.themodindex.creator.maintainer.apicalls.type.FileAddition
import com.github.reviversmc.themodindex.creator.maintainer.apicalls.type.FileDeletion

/**
 * Contains GH actions that can be done involving a branch (or ref) of a repository
 * @author ReviversMC
 * @since 1.0.0
 */
interface GHBranch {

    /**
     * Gets the default branch ref of the repository, throwing an [IllegalStateException] if something unexpected happens
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun defaultBranchRef(): String

    /**
     * Check if a [branchName] exists in the repository, throwing an [IllegalStateException] if something unexpected happens
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun doesRefExist(branchName: String): Boolean

    /**
     * Create a new branch with name from [branchCreated]. The branch will be created off the last commit from [branchFrom].
     * It is recommended to call [doesRefExist] before creating a new branch, to avoid creating a branch that already exists.
     * This throws an [IllegalStateException] if something unexpected happens.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createRef(branchFrom: String, branchCreated: String)

    /**
     * Commit the changes in [additions] and [deletions] and update the [branchName] to the new commit, with message [message].
     * This throws an [IllegalStateException] if something unexpected happens.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun commitAndUpdateRef(
        branchName: String,
        message: String,
        additions: List<FileAddition> = emptyList(),
        deletions: List<FileDeletion> = emptyList(),
    )

    /**
     * Create a pull request from [prFromBranch] to [prToBranch] with title [prTitle] and description [prMessage].
     * This throws an [IllegalStateException] if something unexpected happens.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createPullRequest(prFromBranch: String, prToBranch: String, prTitle: String, prMessage: String?)

    /**
     * Merge branch [mergedFromBranchName] into branch [mergedIntoName] with message [commitMessage], without a PR.
     * This throws an [IllegalStateException] if something unexpected happens.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun mergeBranchWithoutPR(mergedIntoName: String, mergedFromBranchName: String, commitMessage: String)
}