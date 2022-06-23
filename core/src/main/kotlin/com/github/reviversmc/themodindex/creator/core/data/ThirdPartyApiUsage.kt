package com.github.reviversmc.themodindex.creator.core.data

import com.github.reviversmc.themodindex.api.data.ManifestJson

/**
 * An enum to represent the different api providers that are used.
 * [ALL_USED] should only be used when all providers are met, and [NONE_USED] should only be used when no providers are met.
 * @author ReviversMC
 * @since 1.0.0
 */
enum class ThirdPartyApiUsage {
    ALL_USED, CURSEFORGE_USED, GITHUB_USED, MODRINTH_USED, NONE_USED;

    companion object {
        fun isAllWorking(workingList: List<ThirdPartyApiUsage> = emptyList()) =
            CURSEFORGE_USED in workingList && GITHUB_USED in workingList && MODRINTH_USED in workingList

        fun isNoneWorking(workingList: List<ThirdPartyApiUsage> = emptyList()) =
            workingList.isEmpty() || workingList == listOf(NONE_USED)
    }
}

/**
 * A class to neatly bundle a [List] of [ManifestJson]s
 * with the [ThirdPartyApiUsage]s that were used to create them, even if they do not affect the end result.
 */
data class ManifestWithApiStatus(
    val thirdPartyApiUsage: List<ThirdPartyApiUsage>,
    val manifests: List<ManifestJson>,
)