package com.github.reviversmc.themodindex.creator.core.filereader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.zip.ZipInputStream

@Serializable
private data class QuiltModJson(@SerialName("quilt_loader") val quiltLoader: QuiltLoader)

@Serializable
private data class QuiltLoader(val id: String)

// Quilt, at least in its early stages, is able to load Fabric mods. Some Fabric mods are thus also classified as Quilt mods
class QuiltFile(modJarInBytes: ByteArray, json: Json): ModFile, FabricFile(modJarInBytes.inputStream(), json) {

    private var quiltLoader: QuiltLoader? = null

    init {
        ZipInputStream(modJarInBytes.inputStream()).use { itemBeingRead ->
            generateSequence { itemBeingRead.nextEntry }.forEach {
                println(it.name)
                if (it.name == "quilt.mod.json")
                    quiltLoader = json.decodeFromString<QuiltModJson>(itemBeingRead.readBytes().decodeToString()).quiltLoader
            }
        }
    }

    override fun modId() = quiltLoader?.id ?: super.modId()

}