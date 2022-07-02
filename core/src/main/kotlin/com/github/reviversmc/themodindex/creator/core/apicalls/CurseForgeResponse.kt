package com.github.reviversmc.themodindex.creator.core.apicalls

/**
 * The [data] wrapper for the api response for a CF mod, as required by CurseForge :(
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class CurseModResponse(val data: CurseModData)

/**
 * The api response for a CF mod. This does NOT contain all the info from the api call.
 * @param id The id of the mod.
 * @param name The name of the mod.
 * @param links The links of the mod.
 * @param authors The authors of the mod.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class CurseModData(
    val id: Int, val name: String,
    // We can't get the license! :(
    val links: CurseModLinks, val authors: List<CurseModAuthor>,
)

/**
 * The links that a mod has.
 * @param websiteUrl The website url of a mod.
 * @param wikiUrl The wiki url of a mod.
 * @param issuesUrl The issues url of a mod.
 * @param sourceUrl The source url of a mod.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class CurseModLinks(val websiteUrl: String, val wikiUrl: String, val issuesUrl: String?, val sourceUrl: String?)

/**
 * The authors of a mod.
 * @param id The id of the author.
 * @param name The name of the author.
 * @param url The url of the author on CurseForge.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class CurseModAuthor(val id: Int, val name: String, val url: String)


/**
 * The [data] wrapper when searching for CF files
 * @param pagination The pagination info.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class CurseFilesResponse(val data: List<CurseFileResponse>, val pagination: CursePagination)

/**
 * The pagination info for the files.
 * @param index A zero based index of the first item that is included in the response.
 * @param pageSize The requested number of items in the response.
 * @param resultCount The actual number of items that are available.
 * @param totalCount The total number of items that are available by the request.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class CursePagination(val index: Int, val pageSize: Int, val resultCount: Int, val totalCount: Int)


/**
 * The api response for a CF file. This does NOT contain all the info from the api call.
 * @param id The id of the file.
 * @param isAvailable Whether the file is available.
 * @param displayName The display name of the file.
 * @param downloadUrl The download url of the file.
 * @param gameVersions The game versions that the file is available for.
 * @author ReviversMC
 * @since 1.0.0
 */
@kotlinx.serialization.Serializable
data class CurseFileResponse(
    val id: Int,
    val isAvailable: Boolean,
    val displayName: String,
    val downloadUrl: String?,
    val gameVersions: List<String>,
)

/**
 * The api response for a CF search.
 * @param data The data of the search.
 * @param pagination The pagination info.
 * @author ReviversMC
 * @since 1.0.0
 */
data class CurseSearchResponse(val data: List<CurseModData>, val pagination: CursePagination)
