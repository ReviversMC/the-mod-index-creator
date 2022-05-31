package com.github.reviversmc.themodindex.creator.core.apicalls

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * A class that represents a call to the CurseForge API.
 * @author ReviversMC
 * @since 1.0.0
 */
interface CurseForgeApiCall {

    /**
     * Gets a CF mod by api call.
     *
     * @param curseForgeApiKey The api key to use.
     * @param modId The mod id.
     * @return The mod, after a call is made. May not contain all information, but rather [CurseForgeResponse.ModResponse].
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v1/mods/{modId}")
    fun mod(
        @Header("x-api-key") curseForgeApiKey: String,
        @Path("modId") modId: Int
    ): Call<CurseForgeResponse.ModResponse>

    /**
     * Gets the versions of a CF mod by api call.
     *
     * @param curseForgeApiKey The api key to use.
     * @param modId The mod id to search for files from.
     * @param modLoaderType The mod loader type to search for files from. You may use [ModLoaderType] enum to assist you.
     * @param maxNumberOfResults The maximum amount of results to return. Max amount is 10000.
     * @return The versions of the mod, after a call is made. May not contain all information, but rather [CurseForgeResponse.FilesResponse].
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v1/mods/{modId}/files")
    fun files(
        @Header("x-api-key") curseForgeApiKey: String,
        @Path("modId") modId: Int,
        @Query("modLoaderType") modLoaderType: Int? = ModLoaderType.ANY.curseNumber,
        @Query("pageSize") maxNumberOfResults: Int? = 10000
    ): Call<CurseForgeResponse.FilesResponse>

    /**
     * A convenience enum to know what mod loader each number is associated with.
     *
     * @param curseNumber The number associated with the mod loader. Use this number in api calls.
     * @author ReviversMC
     * @since 1.0.0
     */
    @Suppress("unused") //We want all the enum values, so that it can be selected by consumers
    enum class ModLoaderType(val curseNumber: Int?) {
        ANY(null),
        FORGE(1),
        CAULDRON(2),
        LITELOADER(3),
        FABRIC(4),
        QUILT(5)
    }
}