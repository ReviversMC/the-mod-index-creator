package com.github.reviversmc.themodindex.creator.core.data

data class ManifestWithApiStatus(
    val thirdPartyApiStatus: List<ThirdPartyApiStatus>,
    val manifestsWithIdentifiers: List<ManifestWithIdentifier>,
)