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
     * Creates a [ManifestWithApiStatus], which contains [ManifestJson]s
     * When used in Kotlin, [curseForgeId] cannot be null, while [modrinthId] can be null.
     * When there is a conflict in data, CurseForge data will be preferred if [preferCurseForgeData] is true.
     * @throws IllegalArgumentException If the wrong index version is targeted.
     * @author ReviversMC
     * @since 1.0.0
     */
    fun createManifestCurseForge(
        curseForgeId: Int,
        modrinthId: String? = null,
        preferCurseForgeData: Boolean = true,
    ): ManifestWithApiStatus

    /**
     * Creates a [ManifestWithApiStatus], which contains [ManifestJson]s
     * When used in Kotlin, [curseForgeMod] cannot be null, while [modrinthId] can be null.
     * When there is a conflict in data, CurseForge data will be preferred if [preferCurseForgeData] is true.
     * @throws IllegalArgumentException If the wrong index version is targeted.
     * @author ReviversMC
     * @since 1.0.0
     */
    fun createManifestCurseForge(
        curseForgeMod: CurseModData,
        modrinthId: String? = null,
        preferCurseForgeData: Boolean = true,
    ): ManifestWithApiStatus

    /**
     * Creates a [ManifestWithApiStatus], which contains [ManifestJson]s
     * When used in Kotlin, [modrinthId] cannot be null, while [curseForgeId] can be null.
     * When there is a conflict in data, Modrinth data will be preferred if [preferModrinthData] is true.
     * @throws IllegalArgumentException If the wrong index version is targeted.
     * @author ReviversMC
     * @since 1.0.0
     */
    //Does not have a default value for [curseForgeId] so that there isn't an ambiguous match when calling this method
    fun createManifestModrinth(
        modrinthId: String,
        curseForgeId: Int?,
        preferModrinthData: Boolean = true,
    ): ManifestWithApiStatus

    /**
     * Creates a [ManifestWithApiStatus], which contains [ManifestJson]s
     * When used in Kotlin, [modrinthId] cannot be null, while [curseForgeMod] can be null.
     * When there is a conflict in data, Modrinth data will be preferred if [preferModrinthData] is true.
     * @throws IllegalArgumentException If the wrong index version is targeted.
     * @author ReviversMC
     * @since 1.0.0
     */
    fun createManifestModrinth(
        modrinthId: String,
        curseForgeMod: CurseModData? = null,
        preferModrinthData: Boolean = true,
    ): ManifestWithApiStatus

}
