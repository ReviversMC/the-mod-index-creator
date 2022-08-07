package com.github.reviversmc.themodindex.creator.core.filereader

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream

@Serializable
private data class RiftModJson(val id: String)

class RiftFile(modJar: InputStream, json: Json): ModFile {

    private var riftModJson: RiftModJson? = null

    init {
        ZipInputStream(modJar).use { itemBeingRead ->
            generateSequence { itemBeingRead.nextEntry }.forEach {
                if (it.name == "riftmod.json")
                    riftModJson = json.decodeFromString(itemBeingRead.readBytes().decodeToString())
            }
        }
    }

    override fun modId() = riftModJson?.id
}
