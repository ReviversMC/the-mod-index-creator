package com.github.reviversmc.themodindex.creator.core.apicalls

import com.github.reviversmc.themodindex.creator.core.CreatorLoader
import retrofit2.Call
import retrofit2.http.*

/**
 * A class that represents a call to the CurseForge API.
 * @author ReviversMC
 * @since 1.0.0
 */
interface CurseForgeApiCall {

    /**
     * Returns a CF [CurseModResponse], obtained by its [modId].
     * In order to make a successful api call, A [curseForgeApiKey] is required for authentication.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v1/mods/{modId}")
    fun mod(
        @Header("x-api-key") curseForgeApiKey: String,
        @Path("modId") modId: Int,
    ): Call<CurseModResponse>

    /**
     * Returns the versions ([CurseFilesResponse]) of a CF mod, obtained by its [modId].
     * In order to make a successful api call, A [curseForgeApiKey] is required for authentication.
     * A [modLoaderType] obtained using [CreatorLoader] can be used to fine tune the search,
     * and [maxNumberOfResults] con be used to increase/decrease the maximum number of results returned.
     * For stability, it is recommended to leave [maxNumberOfResults] null, for CF to decide a number of results to return that is guaranteed to work.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v1/mods/{modId}/files")
    fun files(
        @Header("x-api-key") curseForgeApiKey: String,
        @Path("modId") modId: Int,
        @Query("modLoaderType") modLoaderType: Int? = CreatorLoader.ANY.curseNumber,
        @Query("pageSize") maxNumberOfResults: Int? = null,
    ): Call<CurseFilesResponse>


    /**
     * Returns a [CurseSearchResponse] for a search query, offset by [index].
     * In order to make a successful api call, A [curseForgeApiKey] is required for authentication.
     * A query will return results starting from [index] to [index] + [maxNumberOfResults] from a 0 index list of mods.
     * It is recommended to leave [maxNumberOfResults] null, for CF to decide a number of results to return that is guaranteed to work.
     */
    @GET("/v1/mods/search?gameId=432&classId=6") // Minecraft's game id is 432, and the class id for mods is 6
    fun search(
        @Header("x-api-key") curseForgeApiKey: String,
        @Query("index") index: Int,
        @Query("pageSize") maxNumberOfResults: Int? = null,
    ): Call<CurseSearchResponse>
}