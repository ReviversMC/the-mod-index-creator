package com.github.reviversmc.themodindex.creator.core.modreader

import cc.ekblad.toml.TomlMapper
import cc.ekblad.toml.decode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private data class ModsToml(val mods: List<ModsTomlTable>)
private data class ModsTomlTable(val modId: String)

internal class ForgeFile(modFileInBytes: ByteArray, toml: TomlMapper) : ModFile {

    private val modsToml: ModsToml = toml.decode(modFileInBytes.decodeToString())
    override fun modId() = modsToml.mods.firstOrNull()?.modId
}

@Serializable
private data class McmodInfo(@SerialName("modid") val modId: String)

internal class LegacyForgeFile(modFileInBytes: ByteArray, json: Json) : ModFile {
    private val mcmodInfo: McmodInfo? = json.decodeFromString<List<McmodInfo>>(modFileInBytes.decodeToString()).firstOrNull()
    override fun modId() = mcmodInfo?.modId
}
