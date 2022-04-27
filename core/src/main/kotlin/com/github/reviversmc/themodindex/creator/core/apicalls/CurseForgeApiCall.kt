package com.github.reviversmc.themodindex.creator.core.apicalls

import java.io.IOException

/**
 * A class that represents a call to the CurseForge API.
 * @author ReviversMC
 * @since 1.0.0-1.0.0
 */
interface CurseForgeApiCall {

    /**
     * Gets a CF mod by api call.
     *
     * @param modId The mod id
     * @return The mod. May not contain all information, but rather [CurseForgeResponse.ModResponse]
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0-1.0.0
     */
    fun mod(modId: String): CurseForgeResponse.ModResponse?

    /**
     * Gets the versions of a CF mod by api call.
     *
     * @param modId The mod id to search for files from
     * @param maxResult The maximum amount of results to return
     * @return The versions of the mod. May not contain all information, but rather [CurseForgeResponse.FilesResponse]
     * @throws IOException If the api call fails.
     * @author ReviversMC
     * @since 1.0.0-1.0.0
     */
    fun files(modId: String, modLoaderType: ModLoaderType = ModLoaderType.ANY, maxResult: Int = Int.MAX_VALUE): CurseForgeResponse.FilesResponse?

    enum class ModLoaderType(val curseNumber: Int) {
        ANY(0),
        FORGE(1),
        CAULDRON(2),
        LITELOADER(3),
        FABRIC(4)
        //TODO Create for quilt when it is added
    }
}