package com.github.reviversmc.themodindex.creator.core.apicalls

import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse.ProjectResponse
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse.VersionResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

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