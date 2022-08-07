package com.github.reviversmc.themodindex.creator.core.filereader

import cc.ekblad.toml.TomlMapper
import cc.ekblad.toml.decode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream

private data class ModsToml(val mods: List<ModsTomlTable>)
private data class ModsTomlTable(val modId: String)

@Serializable
private data class McModInfo(@SerialName("modid") val modId: String)

class Forge(modJar: InputStream, json: Json, toml: TomlMapper) : ModFile {

    private var modsToml: ModsTomlTable? = null
    private var mcModInfo: McModInfo? = null

    // Older versions of Forge used mcmod.info, while newer versions use mods.toml. mcmod.info is essentially json.
    init {
        ZipInputStream(modJar).use { itemBeingRead ->
            while (true) {
                itemBeingRead.nextEntry?.let {
                    // If required file is found, return null. That will cause the elvis operator to run and break the while loop
                    when (it.name) {
                        "META-INF/mods.toml" -> {
                            modsToml =
                                toml.decode<ModsToml>(itemBeingRead.readBytes().decodeToString()).mods.firstOrNull()
                            return@let null
                        }

                        "mcmod.info" -> {
                            mcModInfo = json.decodeFromString<List<McModInfo>>(itemBeingRead.readBytes().decodeToString())
                                .firstOrNull()
                            return@let null
                        }

                        else -> return@let it
                    }
                } ?: break
            }
        }

        println(modsToml)
        println(mcModInfo)
    }

    override fun modId() = modsToml?.modId ?: mcModInfo?.modId
}
