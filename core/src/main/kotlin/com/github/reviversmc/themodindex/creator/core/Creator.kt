package com.github.reviversmc.themodindex.creator.core

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.core.apicalls.CurseModData
import com.github.reviversmc.themodindex.creator.core.data.ManifestWithApiStatus

interface Creator {

    /*
    We have no need to allow the passing of Modrinth project data. CF data is purely so that we can reduce api calls.
    We won't have that kind of information from Modrinth
    */
    /**
     * Creates a [ManifestWithApiStatus] for specified loaders [creatorLoaders], which contains [ManifestJson]s
     * When used in Kotlin, [curseForgeId] cannot be null, while [modrinthId] can be null.
     * When there is a conflict in data, CurseForge data will be preferred if [preferCurseForgeData] is true.
     * @throws IllegalArgumentException If the wrong index version is targeted.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createManifestCurseForge(
        curseForgeId: Int,
        creatorLoaders: List<CreatorLoader> = listOf(CreatorLoader.ANY),
        modrinthId: String? = null,
        preferCurseForgeData: Boolean = true,
    ): ManifestWithApiStatus

    /**
     * Creates a [ManifestWithApiStatus] for specified loaders [creatorLoaders], which contains [ManifestJson]s
     * When used in Kotlin, [curseForgeMod] cannot be null, while [modrinthId] can be null.
     * When there is a conflict in data, CurseForge data will be preferred if [preferCurseForgeData] is true.
     * @throws IllegalArgumentException If the wrong index version is targeted.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createManifestCurseForge(
        curseForgeMod: CurseModData,
        creatorLoaders: List<CreatorLoader> = listOf(CreatorLoader.ANY),
        modrinthId: String? = null,
        preferCurseForgeData: Boolean = true,
    ): ManifestWithApiStatus

    /**
     * Creates a [ManifestWithApiStatus] for specified loaders [creatorLoaders], which contains [ManifestJson]s
     * When used in Kotlin, [modrinthId] cannot be null, while [curseForgeId] can be null.
     * When there is a conflict in data, Modrinth data will be preferred if [preferModrinthData] is true.
     * @throws IllegalArgumentException If the wrong index version is targeted.
     * @author ReviversMC
     * @since 1.0.0
     */
    // Does not have a default value for [curseForgeId] so that there isn't an ambiguous match when calling this method
    suspend fun createManifestModrinth(
        modrinthId: String,
        curseForgeId: Int?,
        creatorLoaders: List<CreatorLoader> = listOf(CreatorLoader.ANY),
        preferModrinthData: Boolean = true,
    ): ManifestWithApiStatus

    /**
     * Creates a [ManifestWithApiStatus] for specified loaders [creatorLoaders], which contains [ManifestJson]s
     * When used in Kotlin, [modrinthId] cannot be null, while [curseForgeMod] can be null.
     * When there is a conflict in data, Modrinth data will be preferred if [preferModrinthData] is true.
     * @throws IllegalArgumentException If the wrong index version is targeted.
     * @author ReviversMC
     * @since 1.0.0
     */
    suspend fun createManifestModrinth(
        modrinthId: String,
        creatorLoaders: List<CreatorLoader> = listOf(CreatorLoader.ANY),
        curseForgeMod: CurseModData? = null,
        preferModrinthData: Boolean = true,
    ): ManifestWithApiStatus

}

enum class CreatorLoader(val curseNumber: Int?, val modrinthCategory: String?) {
    @Suppress("unused") // We want all the enum values, so that it can be selected by consumers
    ANY(null, null),
    FORGE(1, "forge"),
    CAULDRON(2, null),
    LITELOADER(3, "liteloader"),
    FABRIC(4, "fabric"),
    QUILT(5, "quilt"),
    RIFT(null, "rift"),
    MODLOADER(null, "modloader"),
}

