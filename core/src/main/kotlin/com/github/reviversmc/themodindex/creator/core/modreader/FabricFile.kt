package com.github.reviversmc.themodindex.creator.core.modreader

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream

@Serializable
private data class FabricModJson(val id: String)

open class FabricFile(modJar: InputStream, json: Json): ModFile {

    private var fabricModJson: FabricModJson? = null

    init {
        ZipInputStream(modJar).use { itemBeingRead ->
            generateSequence { itemBeingRead.nextEntry }.forEach {
                if (it.name == "fabric.mod.json")
                        fabricModJson = json.decodeFromString(itemBeingRead.readBytes().decodeToString())
                }
        }
    }

    override fun modId() = fabricModJson?.id
}