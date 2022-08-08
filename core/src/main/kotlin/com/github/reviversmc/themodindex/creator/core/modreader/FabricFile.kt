package com.github.reviversmc.themodindex.creator.core.modreader

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class FabricModJson(val id: String)

internal class FabricFile(modFileInBytes: ByteArray, json: Json): ModFile {

    private var fabricModJson: FabricModJson = json.decodeFromString(modFileInBytes.decodeToString())
    override fun modId() = fabricModJson.id
}