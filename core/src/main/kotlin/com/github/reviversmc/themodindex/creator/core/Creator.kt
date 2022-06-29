package com.github.reviversmc.themodindex.creator.core

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.core.data.ManifestWithApiStatus

interface Creator {

    /**
     * Creates a [ManifestWithApiStatus], which contains [ManifestJson]s
     * When used in Kotlin, [curseForgeId] cannot be null, while [modrinthId] can be null.
     * When there is a conflict in data, CurseForge data will be preferred if [preferCurseForgeData] is true.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createManifestCurseForge(
        curseForgeId: Int,
        modrinthId: String? = null,
        preferCurseForgeData: Boolean = true,
    ): ManifestWithApiStatus

    /**
     * Creates a [ManifestWithApiStatus], which contains [ManifestJson]s
     * When used in Kotlin, [modrinthId] cannot be null, while [curseForgeId] can be null.
     * When there is a conflict in data, Modrinth data will be preferred if [preferModrinthData] is true.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createManifestModrinth(
        modrinthId: String,
        curseForgeId: Int? = null,
        preferModrinthData: Boolean = true,
    ): ManifestWithApiStatus

}
