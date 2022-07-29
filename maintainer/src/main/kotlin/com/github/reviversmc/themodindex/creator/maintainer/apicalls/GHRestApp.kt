package com.github.reviversmc.themodindex.creator.maintainer.apicalls

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface GHRestApp {

    @Headers("Accept: application/vnd.github+json")
    @GET("/repos/{owner}/{repo}/installation")
    fun installation(
        @Header("Authorization") jwt: String,
        @Path("owner") repoOwner: String,
        @Path("repo") repoName: String,
    ): Call<AppInstallationResponse>

    @Headers("Accept: application/vnd.github+json")
    @POST("/app/installations/{installation_id}/access_tokens")
    fun createAccessToken(
        @Header("Authorization") jwt: String,
        @Path("installation_id") installationId: Long,
    ): Call<AccessTokenResponse>
}
