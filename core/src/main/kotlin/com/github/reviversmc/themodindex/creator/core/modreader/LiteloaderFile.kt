package com.github.reviversmc.themodindex.creator.core.modreader

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class LitemodJson(val name: String)

internal class LiteloaderFile(modJarInBytes: ByteArray, json: Json): ModFile {

    private var litemodJson: LitemodJson = json.decodeFromString(modJarInBytes.decodeToString())
    override fun modId() = litemodJson.name
}
