package com.github.reviversmc.themodindex.creator.core.modreader

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class RiftModJson(val id: String)

internal class RiftFile(modJarInBytes: ByteArray, json: Json): ModFile {

    private var riftModJson: RiftModJson = json.decodeFromString(modJarInBytes.decodeToString())
    override fun modId() = riftModJson.id
}
