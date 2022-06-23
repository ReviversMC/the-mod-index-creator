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
        /**
         * Returns if the contents of [usedList] is effectively [ALL_USED].
         * @author ReviversMC
         * @since 1.0.0
         */
        fun isAllWorking(usedList: List<ThirdPartyApiUsage> = emptyList()) =
            CURSEFORGE_USED in usedList && GITHUB_USED in usedList && MODRINTH_USED in usedList || usedList == listOf(ALL_USED)

        /**
         * Returns if the contents of [usedList] is effectively [NONE_USED]
         * @author ReviversMC
         * @since 1.0.0
         */
        fun isNoneWorking(usedList: List<ThirdPartyApiUsage> = emptyList()) =
            usedList.isEmpty() || usedList == listOf(NONE_USED)
    }
}

/**
 * A class to neatly bundle a [List] of [ManifestJson]s
 * with the [ThirdPartyApiUsage]s that were used to create them, even if they do not affect the end result.
 * @author ReviversMC
 * @since 1.0.0
 */
data class ManifestWithApiStatus(
    val thirdPartyApiUsage: List<ThirdPartyApiUsage>,
    val manifests: List<ManifestJson>,
)