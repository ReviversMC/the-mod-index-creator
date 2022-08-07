package com.github.reviversmc.themodindex.creator.core.filereader

/**
 * A reader for a specific mod's file (e.g. mods.toml for Forge, fabric.mod.json for Fabric, etc)
 * @since 1.0.0
 * @author ReviversMC
 */
interface ModFile {

    /**
     * Reads the file and returns the mod's modId
     * @since 1.0.0
     * @author ReviversMC
     */
    fun modId(): String?
}
