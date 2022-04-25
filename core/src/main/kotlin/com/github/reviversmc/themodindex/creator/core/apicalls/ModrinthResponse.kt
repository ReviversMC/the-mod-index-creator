package com.github.reviversmc.themodindex.creator.core.apicalls

import kotlinx.serialization.SerialName

/**
 * This does NOT contain all the info that Modrinth provides, only the info that we need.
 */
open class ModrinthResponse {

    /**
     * The api response for a Modrinth project. This does NOT contain all the info from the api call.
     * @param id The id of the project.
     * @param title The title of the project.
     * @param projectLicense The license of the project.
     * @param issuesUrl The url of the issues for the project.
     * @param sourceUrl The url of the source code for the project.
     * @param wikiUrl The url of the wiki for the project.
     * @param discordUrl The url of the discord for the project.
     * @param donationUrls The urls of the donation links for the project.
     * @param versions The versions of the project.
     */
    data class ProjectResponse(
        val id: String,
        val title: String,
        val projectLicense: ProjectLicense?,
        @SerialName("issues_url") val issuesUrl: String,
        @SerialName("source_url") val sourceUrl: String,
        @SerialName("wiki_url") val wikiUrl: String,
        @SerialName("discord_url") val discordUrl: String,
        val donationUrls: List<DonationUrl>,
        val versions: List<String>
    ) {

        /**
         * The license of the project. This does NOT contain all the info from the api call.
         * @param id The id of the license (e.g. "mit").
         */
        data class ProjectLicense(val id: String)

        /**
         * A donation url of the project. This does NOT contain all the info from the api call.
         * @param platform The platform of the donation url (e.g. "paypal").
         * @param url The url of the donation url.
         */
        data class DonationUrl(val platform: String, val url: String)
    }

    /**
     * The api response for a Modrinth team.
     * @param entries The entries of the version.
     */
    data class TeamResponse(val entries: List<TeamEntry>) {
        /**
         * The entry of a Modrinth team. This does NOT contain all the info from the api call.
         * @param userResponse The user of the team.
         * @param role The role of the user in the team.
         */
        data class TeamEntry(val userResponse: UserResponse, val role: String)
    }


    /**
     * The api response for a Modrinth user. This does NOT contain all the info from the api call.
     * @param username The username of the user.
     */
    data class UserResponse(val username: String)

    /**
     * The api response for a Modrinth version. This does NOT contain all the info from the api call.
     * @param name
     * @param gameVersions
     * @param files
     */
    data class VersionResponse(
        val name: String,
        val gameVersions: List<String>,
        val loaders: List<String>,
        val files: List<VersionFile>
    ) {
        /**
         * A file of a Modrinth version. This does NOT contain all the info from the api call.
         * @param hashes The hashes of the file.
         * @param url The url of the file.
         * @param primary Whether the file is the primary file.
         */
        data class VersionFile(val hashes: VersionHash, val url: String, val primary: Boolean) {
            /**
             * The hash of a Modrinth version file. This does NOT contain all the info from the api call.
             * @param sha1 The sha1 hash of the file.
             */
            data class VersionHash(val sha1: String)
        }
    }
}

