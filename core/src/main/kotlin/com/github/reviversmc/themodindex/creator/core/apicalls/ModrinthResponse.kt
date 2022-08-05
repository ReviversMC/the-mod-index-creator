package com.github.reviversmc.themodindex.creator.core.apicalls

import kotlinx.serialization.SerialName

/**
 * The api response for a Modrinth project. This does NOT contain all the info from the api call.
 * @param id The id of the project.
 * @param title The title of the project.
 * @param slug The slug of the project.
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
data class ModrinthProjectResponse(
    val id: String,
    val title: String,
    val slug: String,
    val license: ModrinthLicense?,
    @SerialName("issues_url") val issuesUrl: String?,
    @SerialName("source_url") val sourceUrl: String?,
    @SerialName("wiki_url") val wikiUrl: String?,
    @SerialName("discord_url") val discordUrl: String?,
    @SerialName("donation_urls") val donationUrls: List<ModrinthDonationUrl>?,
    val versions: List<String>,
)

/**
 * The license of the project. This does NOT contain all the info from the api call.
 * @param id The id of the license (e.g. "mit").
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class ModrinthLicense(val id: String)

/**
 * A donation url of the project. This does NOT contain all the info from the api call.
 * @param platform The platform of the donation url (e.g. "paypal").
 * @param url The url of the donation url.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class ModrinthDonationUrl(val platform: String, val url: String)

/**
 * The api response for a search query
 * @param hits The [ModrinthSearchHit]s of the search query.
 * @param offset The number of results skipped by the query.
 * @param limit The number of results returned by the query.
 * @param totalHits The total number projects that exist for the query, not just the ones returned.
 *                  E.g. a search with no filters will return the total number of projects as the totalHits,
 *                  but only return the number of projects specified by the limit/maximum allowed by Modrinth
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class ModrinthSearchResponse(
    val hits: List<ModrinthSearchHit>, val offset: Int, val limit: Int, @SerialName("total_hits") val totalHits: Int,
)

/**
 * This does not give as much information as [ModrinthProjectResponse]. Prefer using [ModrinthProjectResponse] when possible
 *
 * @param id The id of the project.
 * @param title The title of the project.
 * @param license The license of the project.
 * @param author The author of the project.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class ModrinthSearchHit(
    @SerialName("project_id") val id: String,
    val title: String,
    val license: String,
    val author: String,
)

/**
 * The entry of a Modrinth team. This does NOT contain all the info from the api call.
 * @param userResponse The user of the team.
 * @param role The role of the user in the team.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class ModrinthTeamResponse(@SerialName("user") val userResponse: ModrinthUserResponse, val role: String)

/**
 * The api response for a Modrinth user. This does NOT contain all the info from the api call.
 * @param username The username of the user.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class ModrinthUserResponse(val username: String)

/**
 * The api response for a Modrinth version. This does NOT contain all the info from the api call.
 * @param name The name of the version.
 * @param dependencies The relations this version has with other projects.
 * @param gameVersions The Minecraft versions of this Modrinth version.
 * @param loaders The loaders of this Modrinth version (e.g. quilt, fabric, forge, etc).
 * @param projectId The id of the project this version belongs to.
 * @param files The files of the version.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class ModrinthVersionResponse(
    val name: String,
    val dependencies: List<ModrinthDependency> = emptyList(),
    @SerialName("game_versions") val gameVersions: List<String>,
    val loaders: List<String>,
    @SerialName("project_id") val projectId: String,
    val files: List<ModrinthVersionFile>,
)

/**
 * The api response for a modrinth dependency.
 * @param versionId The id for a specific dependency version.
 * @param projectId The id of the project this dependency belongs to.
 * @param dependencyType The type of dependency (or relation) that the dependency has with the parent project. All options can be found in [ModrinthDependencyType].
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class ModrinthDependency(
    @SerialName("version_id") val versionId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("dependency_type") val dependencyType: String,
)

/**
 * All possible dependency types for a Modrinth dependency.
 * @author ReviversMC
 * @since 1.0.0
 */
@Suppress("unused") // Want all fields to be shown in enum
enum class ModrinthDependencyType(val modrinthString: String) {
    REQUIRED("required"),
    OPTIONAL("optional"),
    INCOMPATIBLE("incompatible"),
    EMBEDDED("embedded"),
}

/**
 * A file of a Modrinth version. This does NOT contain all the info from the api call.
 * @param hashes The hashes of the file.
 * @param url The url of the file.
 * @param primary Whether the file is the primary file of the version (e.g. of a classified non-primary file could be a source file). Declaration of primary file is up to author.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class ModrinthVersionFile(val hashes: ModrinthVersionHash, val url: String, val primary: Boolean)

/**
 * The hash of a Modrinth version file. This does NOT contain all the info from the api call.
 * @param sha512 The sha512 hash of the file.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class ModrinthVersionHash(val sha512: String)

