package com.github.reviversmc.themodindex.creator.core.modreader

import cc.ekblad.toml.TomlMapper
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream

class ModMatcher(modJar: InputStream, json: Json, toml: TomlMapper) : ModFile {

    private val matchedMod: ModFile?

    init {
        ZipInputStream(modJar).use { itemBeingRead ->
            generateSequence { itemBeingRead.nextEntry }.find {
                when (it.name) {
                    "fabric.mod.json", "litemod.json", "META-INF/mods.toml", "mcmod.info", "quilt.mod.json", "riftmod.json" -> true
                    else -> false
                }
            }.also {// This also includes null entries!
                matchedMod = when (it?.name ?: "") {
                    "fabric.mod.json" -> FabricFile(itemBeingRead.readBytes(), json)
                    "litemod.json" -> LiteloaderFile(itemBeingRead.readBytes(), json)
                    "META-INF/mods.toml", -> ForgeFile(itemBeingRead.readBytes(), toml)
                    "mcmod.info" -> LegacyForgeFile(itemBeingRead.readBytes(), json)
                    "quilt.mod.json" -> QuiltFile(itemBeingRead.readBytes(), json)
                    "riftmod.json" -> RiftFile(itemBeingRead.readBytes(), json)
                    else -> null
                }
            }
        }
    }


    override fun modId() = matchedMod?.modId()
}
