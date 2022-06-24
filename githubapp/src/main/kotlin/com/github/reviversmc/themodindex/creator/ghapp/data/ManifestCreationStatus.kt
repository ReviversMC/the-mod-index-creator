package com.github.reviversmc.themodindex.creator.ghapp.data

import com.github.reviversmc.themodindex.api.data.ManifestJson

/**
 * Specify the review status of generated [ManifestJson]s
 * @author ReviversMC
 * @since 1.0.0
 */
enum class ReviewStatus {
    APPROVED_FOR_USE, MANUAL_REVIEW_REQUIRED, MARKED_FOR_REMOVAL, NO_CHANGE, THIRD_PARTY_API_FAILURE
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
