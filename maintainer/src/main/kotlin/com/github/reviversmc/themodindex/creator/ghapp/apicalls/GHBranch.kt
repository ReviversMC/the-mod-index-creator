package com.github.reviversmc.themodindex.creator.ghapp.apicalls

import com.github.reviversmc.themodindex.creator.ghapp.apicalls.type.FileAddition
import com.github.reviversmc.themodindex.creator.ghapp.apicalls.type.FileDeletion

interface GHBranch {

    /**
     * Gets the default branch ref of the repository.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun defaultBranchRef(): String

    /**
     * Check if a [branchName] exists in the repository.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun doesRefExist(branchName: String): Boolean

    /**
     * Create a new branch with name from [branchCreated]. The branch will be created off the last commit from [branchFrom].
     * It is recommended to call [doesRefExist] before creating a new branch, to avoid creating a branch that already exists.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createRef(branchFrom: String, branchCreated: String)

    /**
     * Commit the changes in [additions] and [deletions] and update the [branchName] to the new commit, with message [message].
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
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createPullRequest(prFromBranch: String, prToBranch: String, prTitle: String, prMessage: String?)

    /**
     * Merge branch [mergedFromBranchName] into branch [mergedIntoName], without a PR.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun mergeBranchWithoutPR(mergedIntoName: String, mergedFromBranchName: String)
}