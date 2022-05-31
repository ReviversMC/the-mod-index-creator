package com.github.reviversmc.themodindex.creator.core.apicalls

import kotlinx.serialization.SerialName

/**
 * This does NOT contain all the info that Modrinth provides, only the info that we need.
 * @author ReviversMC
 * @since 1.0.0
 */
class ModrinthResponse {

    /**
     * The api response for a Modrinth project. This does NOT contain all the info from the api call.
     * @param id The id of the project.
     * @param title The title of the project.
     * @param license The license of the project.
     * @param issuesUrl The url of the issues for the project.
     * @param sourceUrl The url of the source code for the project.
     * @param wikiUrl The url of the wiki for the project.
     * @param discordUrl The url of the discord for the project.
     * @param donationUrls The urls of the donation links for the project.
     * @param versions The versions of the project.
     * @author ReviversMC
     * @since 1.0.0
     */
    @kotlinx.serialization.Serializable
    data class ProjectResponse(
        val id: String,
        val title: String,
        val license: License?,
        @SerialName("issues_url") val issuesUrl: String?,
        @SerialName("source_url") val sourceUrl: String?,
        @SerialName("wiki_url") val wikiUrl: String?,
        @SerialName("discord_url") val discordUrl: String?,
        @SerialName("donation_urls") val donationUrls: List<DonationUrl>?,
        val versions: List<String>
    ) {

        /**
         * The license of the project. This does NOT contain all the info from the api call.
         * @param id The id of the license (e.g. "mit").
         * @author ReviversMC
         * @since 1.0.0
         */
        @kotlinx.serialization.Serializable
        data class License(val id: String)

        /**
         * A donation url of the project. This does NOT contain all the info from the api call.
         * @param platform The platform of the donation url (e.g. "paypal").
         * @param url The url of the donation url.
         * @author ReviversMC
         * @since 1.0.0
         */
        @kotlinx.serialization.Serializable
        data class DonationUrl(val platform: String, val url: String)
    }

    /**
     * The api response for a search query
     * @param hits The hits ([ProjectResponse]) of the search query.
     * @param offset The number of results skipped by the query.
     * @param limit The number of results returned by the query.
     * @param totalHits The total number projects that exist for the query, not just the ones returned.
     *                  E.g. a search with no filters will return the total number of projects as the totalHits,
     *                  but only return the number of projects specified by the limit/maximum allowed by Modrinth
     * @author ReviversMC
     * @since 1.0.0
     */
    @kotlinx.serialization.Serializable
    data class SearchResponse(
        val hits: List<ProjectResponse>, val offset: Int, val limit: Int, @SerialName("total_hits") val totalHits: Int
    )

    /**
     * The entry of a Modrinth team. This does NOT contain all the info from the api call.
     * @param userResponse The user of the team.
     * @param role The role of the user in the team.
     * @author ReviversMC
     * @since 1.0.0
     */
    @kotlinx.serialization.Serializable
    data class TeamResponse(@SerialName("user") val userResponse: UserResponse, val role: String)

    /**
     * The api response for a Modrinth user. This does NOT contain all the info from the api call.
     * @param username The username of the user.
     * @author ReviversMC
     * @since 1.0.0
     */
    @kotlinx.serialization.Serializable
    data class UserResponse(val username: String)

    /**
     * The api response for a Modrinth version. This does NOT contain all the info from the api call.
     * @param name The name of the version.
     * @param gameVersions The Minecraft versions of this Modrinth version.
     * @param loaders The loaders of this Modrinth version (e.g. quilt, fabric, forge, etc).
     * @param files The files of the version.
     * @author ReviversMC
     * @since 1.0.0
     */
    @kotlinx.serialization.Serializable
    data class VersionResponse(
        val name: String,
        @SerialName("game_versions") val gameVersions: List<String>,
        val loaders: List<String>,
        val files: List<VersionFile>
    ) {
        /**
         * A file of a Modrinth version. This does NOT contain all the info from the api call.
         * @param hashes The hashes of the file.
         * @param url The url of the file.
         * @author ReviversMC
         * @since 1.0.0
         */
        @kotlinx.serialization.Serializable
        data class VersionFile(val hashes: VersionHash, val url: String) {
            /**
             * The hash of a Modrinth version file. This does NOT contain all the info from the api call.
             * @param sha512 The sha512 hash of the file.
             * @author ReviversMC
             * @since 1.0.0
             */
            @kotlinx.serialization.Serializable
            data class VersionHash(val sha512: String)
        }
    }
}

