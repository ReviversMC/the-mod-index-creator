package com.github.reviversmc.themodindex.creator.core.filereader

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream

@Serializable
private data class LitemodJson(val name: String)

class LiteloaderFile(modJar: InputStream, json: Json): ModFile {

    private var litemodJson: LitemodJson? = null

    init {
        ZipInputStream(modJar).use { itemBeingRead ->
            generateSequence { itemBeingRead.nextEntry }.forEach {
                if (it.name == "litemod.json")
                    litemodJson = json.decodeFromString(itemBeingRead.readBytes().decodeToString())
            }
        }
    }

    override fun modId() = litemodJson?.name
}