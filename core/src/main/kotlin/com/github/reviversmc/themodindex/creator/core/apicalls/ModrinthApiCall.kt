package com.github.reviversmc.themodindex.creator.core.apicalls

import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse.ProjectResponse
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse.VersionResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.IOException

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
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/project/{projectId}")
    fun project(@Path("projectId") projectId: String): Response<ProjectResponse>

    /**
     * Gets a modrinth project via api call, asynchronously.
     *
     * @param projectId The project id, or a slug.
     * @return The project, after a call is made. May not contain all information provided by the api, but rather [ModrinthResponse.ProjectResponse].
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/project/{projectId}")
    suspend fun projectAsync(@Path("projectId") projectId: String): Response<ProjectResponse>

    /**
     * Gets the owner of the team who created a Modrinth project.
     *
     * @param projectId The project id, or a slug.
     * @return The team members, after a call is made. May not contain all information provided by the api, but rather [ModrinthResponse.TeamResponse].
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/project/{projectId}/members")
    fun projectMembers(@Path("projectId") projectId: String): Response<List<ModrinthResponse.TeamResponse>>

    /**
     * Gets the owner of the team who created a Modrinth project, asynchronously
     *
     * @param projectId The project id, or a slug.
     * @return The team members, after a call is made. May not contain all information provided by the api, but rather [ModrinthResponse.TeamResponse].
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/project/{projectId}/members")
    suspend fun projectMembersAsync(@Path("projectId") projectId: String): Response<List<ModrinthResponse.TeamResponse>>

    /**
     * Gets a modrinth file via api call.
     *
     * @param projectId The project id, or a slug.
     * @return The info of all versions, after a call is made. May not contain all information provided by the api, but rather [ModrinthResponse.VersionResponse].
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/project/{projectId}/versions")
    fun versions(@Path("projectId") projectId: String): Response<List<VersionResponse>>

    /**
     * Gets a modrinth file via api call, asynchronously.
     *
     * @param projectId The project id, or a slug.
     * @return The info of all versions, after a call is made. May not contain all information provided by the api, but rather [ModrinthResponse.VersionResponse].
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0
     */
    @GET("/project/{projectId}/versions")
    suspend fun versionsAsync(@Path("projectId") projectId: String): Response<List<VersionResponse>>
}