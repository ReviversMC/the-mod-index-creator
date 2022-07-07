package com.github.reviversmc.themodindex.creator.core.apicalls

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * A class that represents a call to the Modrinth API.
 * @author ReviversMC
 * @since 1.0.0
 */
interface ModrinthApiCall {

    companion object {
        const val userAgent = "User-Agent: reviversmc/the-mod-index-creator/1.0.0"
    }

    /**
     * Returns a Modrinth [ModrinthProjectResponse], obtained by its [projectId].
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v2/project/{projectId}")
    @Headers(userAgent)
    fun project(@Path("projectId") projectId: String): Call<ModrinthProjectResponse>

    /**
     * Returns the team members for a Modrinth project, obtained by its [projectId].
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v2/project/{projectId}/members")
    @Headers(userAgent)
    fun projectMembers(@Path("projectId") projectId: String): Call<List<ModrinthTeamResponse>>

    /**
     * Returns projects present in the Modrinth API.
     * Each hit in [ModrinthSearchHit] does not provide as much information as [ModrinthProjectResponse].
     * Use [project] to get more information on a project.
     *
     * Filtering can be done using the following params:
     *
     * @param query The query to search for (e.g. a project name)
     * @param searchMethod The search method that results are sorted by. Use [SearchMethod] for all available options
     * @param offset The number of results to skip. Results will thus be from offset to offset + limit.
     * @param limit The number of results to return. Leave null if you want the modrinth default limit.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v2/search")
    @Headers(userAgent)
    fun search(
        @Query("query") query: String? = null,
        @Query("index") searchMethod: String? = SearchMethod.DEFAULT.modrinthString,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Call<ModrinthSearchResponse>

    /**
     * A convenience enum for knowing which search method ([modrinthString]) to use.
     * Use [modrinthString] instead of the [name] property, as [modrinthString] is correctly capitalized.
     * @author ReviversMC
     * @since 1.0.0
     */

    @Suppress("unused") // We want all the enum values, so that it can be selected by consumers.
    enum class SearchMethod(val modrinthString: String?) {
        DEFAULT(null), DOWNLOADS("downloads"), FOLLOWS("follows"), NEWEST("newest"), RELEVANCE("relevance"), UPDATED("updated")
    }

    /**
     * Returns all modrinth [ModrinthVersionResponse]s for a project, obtained using its [projectId].
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v2/project/{projectId}/version")
    @Headers(userAgent)
    fun versions(@Path("projectId") projectId: String): Call<List<ModrinthVersionResponse>>

    /**
     * Returns a specific modrinth [ModrinthVersionResponse], obtained by its [versionId].
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v2/version/{id}")
    fun version(@Path("id") versionId: String): Call<ModrinthVersionResponse>
}