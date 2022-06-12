package com.github.reviversmc.themodindex.creator.core.apicalls

import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse.ProjectResponse
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse.VersionResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * A class that represents a call to the Modrinth API.
 * @author ReviversMC
 * @since 1.0.0
 */
interface ModrinthApiCall {

    /**
     * Gets a modrinth project via api call.
     *
     * @param projectId The project id, or a slug.
     * @return The project, after a call is made. May not contain all information provided by the api, but rather [ModrinthResponse.ProjectResponse].
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v2/project/{projectId}")
    fun project(@Path("projectId") projectId: String): Call<ProjectResponse>

    /**
     * Gets the owner of the team who created a Modrinth project.
     *
     * @param projectId The project id, or a slug.
     * @return The team members, after a call is made. May not contain all information provided by the api, but rather [ModrinthResponse.TeamResponse].
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v2/project/{projectId}/members")
    fun projectMembers(@Path("projectId") projectId: String): Call<List<ModrinthResponse.TeamResponse>>

    /**
     * Gets projects present in the Modrinth API.
     * Each hit in [ModrinthResponse.SearchResponse.hits] does not provide as much information as [ModrinthResponse.ProjectResponse].
     * Use [project] to get more information on a project.
     *
     * @param query The query to search for (e.g. a project name)
     * @param searchMethod The search method that results are sorted by. Use [SearchMethod] for all available options
     * @param offset The number of results to skip. Results will thus be from offset to offset + limit.
     * @param limit The number of results to return. Leave null if you want the modrinth default limit.
     * @return The projects, after a call is made.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v2/search")
    fun search(
        @Query("query") query: String? = null,
        @Query("index") searchMethod: String? = SearchMethod.DEFAULT.modrinthString,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null,
    ): Call<ModrinthResponse.SearchResponse>

    /**
     * A convenience enum for knowing which search method to use.
     *
     * @param modrinthString The string to use in the api call. Use this instead of the [name] property.
     * @author ReviversMC
     * @since 1.0.0
     */

    @Suppress("unused") // We want all the enum values, so that it can be selected by consumers.
    enum class SearchMethod(val modrinthString: String?) {
        DEFAULT(null), DOWNLOADS("downloads"), FOLLOWS("follows"), NEWEST("newest"), RELEVANCE("relevance"), UPDATED("updated")
    }

    /**
     * Gets all modrinth files for a project.
     *
     * @param projectId The project id, or a slug.
     * @return The info of all versions, after a call is made. May not contain all information provided by the api, but rather [ModrinthResponse.VersionResponse].
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/v2/project/{projectId}/version")
    fun versions(@Path("projectId") projectId: String): Call<List<VersionResponse>>
}