package com.github.reviversmc.themodindex.creator.core

import com.github.reviversmc.themodindex.api.data.IndexJson
import com.github.reviversmc.themodindex.api.data.ManifestJson

interface Creator {
    /**
     * Creates a fully fleshed out manifest.json file.
     *
     * @param modrinthId   The modrinth id of the mod. Slugs not recommended.
     * @param curseForgeId The curseforge id of the mod. Slugs not recommended.
     * @return A [ManifestJson] object, with the key being the mod loader.
     * @author ReviversMC
     * @since 1.0.0
     */
    fun createManifest(modrinthId: String?, curseForgeId: Int?): Map<String, ManifestJson>

    /**
     * Adds entries to the index.json file if entries are new.
     * @param indexToModify The index.json file to modify.
     * @param manifest The manifest with entries to add to the index.
     * @param genericIdentifier The identifier of the mod in the index (i.e. modLoader:modName).
     * @return A [IndexJson] object, with the key being the mod loader.
     * @author ReviversMC
     * @since 1.0.0
     */
    fun modifyIndex(indexToModify: IndexJson, manifest: ManifestJson, genericIdentifier: String): IndexJson
}