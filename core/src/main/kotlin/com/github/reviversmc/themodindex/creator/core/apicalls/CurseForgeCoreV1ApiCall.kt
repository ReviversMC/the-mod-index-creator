package com.github.reviversmc.themodindex.creator.core.apicalls

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * V1 CF Core API calls
 * @author ReviversMC
 * @since 1.0.0-1.0.0
 */
class CurseForgeCoreV1ApiCall(
    private val apiKey: String,
    private val json: Json,
    private val okHttpClient: OkHttpClient
) : CurseForgeApiCall {

    private val endpoint = "https://api.curseforge.com"

    override fun mod(modId: String): CurseForgeResponse.ModResponse? {
        val response = okHttpClient.newCall(
            Request.Builder()
                .header("x-api-key", apiKey)
                .url("$endpoint/v1/mods/$modId")
                .build()
        ).execute()

        val pojoResponse = response.body?.string()?.let { json.decodeFromString<CurseForgeResponse.ModResponse>(it) }
        response.close()
        return pojoResponse
    }

    override fun files(modId: String, modLoaderType: CurseForgeApiCall.ModLoaderType, maxResult: Int): CurseForgeResponse.FilesResponse? {
        val response =
            okHttpClient.newCall(
                Request.Builder()
                    .header("x-api-key", apiKey)
                    .url("$endpoint/v1/mods/$modId/files?modLoaderType=${modLoaderType.curseNumber}&pageSize=$maxResult")
                    .build()
            ).execute()

        val pojoResponse = response.body?.string()?.let { json.decodeFromString<CurseForgeResponse.FilesResponse>(it) }
        response.close()
        return pojoResponse
    }
}
