package com.github.reviversmc.themodindex.creator.core.apicalls

import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * V2 Modrinth API calls
 * @author ReviversMC
 * @since 1.0.0
 */
class ModrinthV2ApiCall(private val json: Json, private val okHttpClient: OkHttpClient) : ModrinthApiCall {

    private val endpoint = "https://api.modrinth.com/v2"

    /**
     * Remember to close the response after use
     * @param urlAsString The url to call
     * @return The response from the call
     * @throws IOException If the call fails
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun doApiCall(urlAsString: String): Response {
        val response = okHttpClient.newCall(Request.Builder().url(urlAsString).build()).execute()
        response.header("X-RateLimit-Remaining")?.toInt()?.let {
            if (it <= 0) throw IOException("Rate limit exceeded. Try again in " + response.header("X-RateLimit-Reset") + " seconds.")
        }
        return response
    }

    override fun project(projectId: String): ProjectResponse? {

        val response = doApiCall("$endpoint/project/$projectId")

        val pojoResponse = response.body?.string()?.let { json.decodeFromString<ProjectResponse>(it) }
        response.close()
        return pojoResponse
    }

    override fun projectOwner(projectId: String): String? {

        val response = doApiCall("$endpoint/project/$projectId/members")

        //This MUST be a list. A top level array is returned from the api call.
        val pojoResponse = response.body?.string()?.let { json.decodeFromString<List<TeamResponse>>(it) }
        response.close()

        //Roles are strings, not enum values :(

        pojoResponse?.let { if (it.size == 1) return pojoResponse[0].userResponse.username } ?: return null

        for (entry in pojoResponse) if (entry.role.equals("owner", true)) return entry.userResponse.username


        for (entry in pojoResponse) if (entry.role.equals("coowner", true) || entry.role.equals(
                "co-owner", true
            )
        ) return entry.userResponse.username


        for (entry in pojoResponse) if (entry.role.equals("admin", true)) return entry.userResponse.username

        for (entry in pojoResponse) if (entry.role.equals("moderator", true)) return entry.userResponse.username

        return pojoResponse[0].userResponse.username
    }

    override fun version(fileId: String): VersionResponse? {
        val response = doApiCall("$endpoint/version/$fileId")

        val pojoResponse = response.body?.string()?.let { json.decodeFromString<VersionResponse>(it) }
        response.close()
        return pojoResponse
    }
}
