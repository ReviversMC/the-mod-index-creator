package com.github.reviversmc.themodindex.creator.core.apicalls

import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse.ProjectResponse
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse.VersionResponse
import java.io.IOException

/**
 * A class that represents a call to the Modrinth API.
 * @author ReviversMC
 * @since 1-1.0.0
 */
interface ModrinthApiCall {

    /**
     * Gets a modrinth project via api call.
     *
     * @param projectId The project id, or a slug.
     * @return The project. May not contain all information provided by the api, but rather [ModrinthResponse.ProjectResponse]
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1-1.0.0
     */
    fun project(projectId: String): ProjectResponse?

    /**
     * Gets the owner of the team who created a Modrinth project.
     *
     * @param projectId The project id, or a slug.
     * @return The team owner. May not contain all information provided by the api, but rather [ModrinthResponse.TeamResponse]
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1-1.0.0
     */
    fun projectOwner(projectId: String): String?

    /**
     * Gets a modrinth file via api call.
     *
     * @param fileId The file id.
     * @return The file. May not contain all information provided by the api, but rather [ModrinthResponse.VersionResponse]
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1-1.0.0
     */
    fun version(fileId: String): VersionResponse?
}