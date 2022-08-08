package com.github.reviversmc.themodindex.creator.core.modreader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class QuiltModJson(@SerialName("quilt_loader") val quiltLoader: QuiltLoader)

@Serializable
private data class QuiltLoader(val id: String)

internal class QuiltFile(modJarInBytes: ByteArray, json: Json): ModFile {

    private var quiltLoader: QuiltModJson = json.decodeFromString(modJarInBytes.decodeToString())
    override fun modId() = quiltLoader.quiltLoader.id

}