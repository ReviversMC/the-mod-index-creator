package com.github.reviversmc.themodindex.creator.core.apicalls

/**
 * This does NOT contain all the info that CF provides, only the info that we need.
 * @author ReviversMC
 * @since 1.0.0-1.0.0
 */
class CurseForgeResponse {

    /**
     * The data wrapper for the api response for a CF mod. This does NOT contain all the info from the api call.
     * @param data The [ModData] from the api call, cause CF WRAPS EVERYTHING IN A DATA OBJECT.
     * @author ReviversMC
     * @since 1.0.0-1.0.0
     */
    @kotlinx.serialization.Serializable
    data class ModResponse(val data: ModData) {

        /**
         * The api response for a CF mod. This does NOT contain all the info from the api call.
         * @param id The id of the mod.
         * @param name The name of the mod.
         * @param links The links of the mod.
         * @param authors The authors of the mod.
         * @param allowModDistribution Whether the mod is allowed to be distributed.
         * @author ReviversMC
         * @since 1.0.0-1.0.0
         */
        @kotlinx.serialization.Serializable
        data class ModData(
            val id: Int, val name: String,
            //We can't get the license! :(
            val links: ModLinks, val authors: List<ModAuthor>, val allowModDistribution: Boolean?
        )

        /**
         * The links that a mod has
         * @param websiteUrl The website url of a mod
         * @param wikiUrl The wiki url of a mod
         * @param issuesUrl The issues url of a mod
         * @param sourceUrl The source url of a mod
         * @author ReviversMC
         * @since 1.0.0-1.0.0
         */
        @kotlinx.serialization.Serializable
        data class ModLinks(val websiteUrl: String, val wikiUrl: String, val issuesUrl: String, val sourceUrl: String)

        /**
         * The authors of a mod
         * @param id The id of the author
         * @param name The name of the author
         * @param url The url of the author on CurseForge
         * @author ReviversMC
         * @since 1.0.0-1.0.0
         */
        @kotlinx.serialization.Serializable
        data class ModAuthor(val id: Int, val name: String, val url: String)
    }


    /**
     * The api response when searching for CF files. This does NOT contain all the info from the api call.
     * @param data The [FileResponse]s that were found, cause CF WRAPS EVERYTHING IN A DATA OBJECT
     * @param pagination The pagination info
     * @author ReviversMC
     * @since 1.0.0-1.0.0
     */
    @kotlinx.serialization.Serializable
    data class FilesResponse(val data: List<FileResponse>, val pagination: Pagination) {

        /**
         * The pagination info for the files
         * @param index A zero based index of the first item that is included in the response
         * @param pageSize The requested number of items in the response
         * @param resultCount The actual number of items that are available
         * @param totalCount The total number of items that are available by the request
         * @author ReviversMC
         * @since 1.0.0-1.0.0
         */
        @kotlinx.serialization.Serializable
        data class Pagination(val index: Int, val pageSize: Int, val resultCount: Int, val totalCount: Int)
    }

    /**
     * The api response for a CF file. This does NOT contain all the info from the api call.
     * @param id The id of the file
     * @param isAvailable Whether the file is available
     * @param displayName The display name of the file
     * @param hashes The hashes of the file
     * @param downloadUrl The download url of the file
     * @param gameVersions The game versions that the file is available for
     * @author ReviversMC
     * @since 1.0.0-1.0.0
     */
    @kotlinx.serialization.Serializable
    data class FileResponse(
        val id: Int,
        val isAvailable: Boolean,
        val displayName: String,
        val hashes: List<FileHashes>,
        val downloadUrl: String,
        val gameVersions: List<String>
    ) {
        /**
         * The hashes of a file
         * @param value The hash value
         * @param algo The algorithm used to generate the hash. The int value can be made sense of using [makeSenseOfAlgo]
         * @author ReviversMC
         * @since 1.0.0-1.0.0
         */
        @kotlinx.serialization.Serializable
        data class FileHashes(val value: String, val algo: Int) {
            /**
             * Make sense of the hash algorithm numbers that CF gives
             * @return The name of the algorithm, instead of CF giving numbers :/
             * @author ReviversMC
             * @since 1.0.0-1.0.0
             */
            fun makeSenseOfAlgo(): String = when (algo) {
                1 -> "Sha1"
                2 -> "Md5"
                else -> "Unknown"
            }
        }
    }
}