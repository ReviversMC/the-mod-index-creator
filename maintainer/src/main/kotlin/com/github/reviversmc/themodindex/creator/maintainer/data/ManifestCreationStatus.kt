package com.github.reviversmc.themodindex.creator.maintainer.data

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage

/**
 * Specify the review status of generated [ManifestJson]s
 * @author ReviversMC
 * @since 1.0.0
 */
enum class ReviewStatus {
    APPROVED_GENERIC_IDENTIFIER_CHANGE, APPROVED_UPDATE, CREATION_CONFLICT, MARKED_FOR_REMOVAL, NO_CHANGE, THIRD_PARTY_API_FAILURE, UPDATE_CONFLICT
}

/**
 * A class to neatly bundle a comparison between the [latestManifest] and the [originalManifest].
 * There is an included [reviewStatus], which specifies the status given to the [latestManifest].
 * @author ReviversMC
 * @since 1.0.0
 */
data class ManifestWithCreationStatus(
    val reviewStatus: ReviewStatus,
    val latestManifest: ManifestJson?,
    val originalManifest: ManifestJson,
)

/**
 * A class to neatly bundle a comparison between the [latestManifest] and the [originalManifest].
 * The manifests have not been reviewed. [thirdPartyApiUsage] is provided to assist in the review process.
 * @author ReviversMC
 * @since 1.0.0
 */
data class ManifestPendingReview(val thirdPartyApiUsage: List<ThirdPartyApiUsage>, val latestManifest: ManifestJson?, val originalManifest: ManifestJson)
