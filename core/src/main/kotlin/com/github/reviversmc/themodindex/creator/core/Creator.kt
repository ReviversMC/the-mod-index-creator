package com.github.reviversmc.themodindex.creator.core

import com.github.reviversmc.themodindex.api.data.IndexJson
import com.github.reviversmc.themodindex.creator.core.data.ManifestWithApiStatus
import com.github.reviversmc.themodindex.creator.core.data.ManifestWithIdentifier

interface Creator {

    /**
     * Creates a [ManifestWithApiStatus], which contains [ManifestWithIdentifier]s
     * When used in Kotlin, [curseForgeId] cannot be null, while [modrinthId] can be null.
     * @author ReviversMC
     * @since 1.0.0
     */
    fun createManifestCurseForge(modrinthId: String, curseForgeId: Int? = null): ManifestWithApiStatus

    /**
     * Creates a [ManifestWithApiStatus], which contains [ManifestWithIdentifier]s
     * When used in Kotlin, [modrinthId] cannot be null, while [curseForgeId] can be null.
     * @author ReviversMC
     * @since 1.0.0
     */
    fun createManifestModrinth(modrinthId: String? = null, curseForgeId: Int): ManifestWithApiStatus

    /**
     * Adds [ManifestWithIdentifier] entries to the [indexToModify] if entries are new.
     * Returns the [indexToModify] with the new entries added, or the same index if no new entries were added.
     * @author ReviversMC
     * @since 1.0.0
     */
    fun modifyIndex(indexToModify: IndexJson, manifestWithIdentifier: ManifestWithIdentifier): IndexJson
}