package com.github.reviversmc.themodindex.creator.core.apicalls

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException

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
     * @return The mod. May not contain all information, but rather [CurseForgeResponse.ModResponse].
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v1/mods/{modId}")
    fun mod(@Header("x-api-key") curseForgeApiKey: String, @Path("modId") modId: Int): Response<CurseForgeResponse.ModResponse>

    /**
     * Gets a CF mod by api call, asynchronously.
     *
     * @param curseForgeApiKey The api key to use.
     * @param modId The mod id.
     * @return The mod. May not contain all information, but rather [CurseForgeResponse.ModResponse].
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v1/mods/{modId}")
    suspend fun modAsync(@Header("x-api-key") curseForgeApiKey: String, @Path("modId") modId: Int): Response<CurseForgeResponse.ModResponse>

    /**
     * Gets the versions of a CF mod by api call.
     *
     * @param curseForgeApiKey The api key to use.
     * @param modId The mod id to search for files from.
     * @param modLoaderType The mod loader type to search for files from. You may use [ModLoaderType] enum to assist you.
     * @param maxNumberOfResults The maximum amount of results to return. Max amount is 10000.
     * @return The versions of the mod. May not contain all information, but rather [CurseForgeResponse.FilesResponse].
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v1/mods/{modId}/files")
    fun files(
        @Header("x-api-key") curseForgeApiKey: String,
        @Path("modId") modId: Int,
        @Query("modLoaderType") modLoaderType: Int? = ModLoaderType.ANY.curseNumber,
        @Query("pageSize") maxNumberOfResults: Int? = 10000
    ): Response<CurseForgeResponse.FilesResponse>

    /**
     * Gets the versions of a CF mod by api call, asynchronously.
     *
     * @param curseForgeApiKey The api key to use.
     * @param modId The mod id to search for files from.
     * @param modLoaderType The mod loader type to search for files from. You may use [ModLoaderType] enum to assist you.
     * @param maxNumberOfResults The maximum amount of results to return. Max amount is 10000.
     * @return The versions of the mod. May not contain all information, but rather [CurseForgeResponse.FilesResponse].
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v1/mods/{modId}/files")
    fun filesAsync(
        @Header("x-api-key") curseForgeApiKey: String,
        @Path("modId") modId: Int,
        @Query("modLoaderType") modLoaderType: Int? = ModLoaderType.ANY.curseNumber,
        @Query("pageSize") maxNumberOfResults: Int? = 10000
    ): Response<CurseForgeResponse.FilesResponse>

    enum class ModLoaderType(val curseNumber: Int?) {
        ANY(null),
        FORGE(1),
        CAULDRON(2),
        LITELOADER(3),
        FABRIC(4)
        //TODO Create for quilt when it is added
    }
}