package com.github.reviversmc.themodindex.creator.core.data

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.data.VersionFile

/**
 * A class to neatly bundle a mod's generic identifier (in the format "mod loader:mod name" with its [ManifestJson].
 * This is not [Serializable], as it isn't intended to be serialized to json. This also helps to prevent accidental serialization, instead of serializing the [ManifestJson] directly.
 * @author ReviversMC
 * @since 1.0.0
 */
data class ManifestWithGenericIdentifier(val genericIdentifier: String, val manifestJson: ManifestJson)

/**
 * A class to neatly bundle a mod's identifier (in the format "mod loader:mod name:version hash" with its [VersionFile].
 * This is not [Serializable], as it isn't intended to be serialized to json. This also helps to prevent accidental serialization, instead of serializing the [VersionFile] directly.
 * @author ReviversMC
 * @since 1.0.0
 */
data class VersionWithIdentifier(val identifier: String, val version: VersionFile)

