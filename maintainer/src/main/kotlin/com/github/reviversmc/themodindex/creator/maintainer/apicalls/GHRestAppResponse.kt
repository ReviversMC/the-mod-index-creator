package com.github.reviversmc.themodindex.creator.maintainer.apicalls

import kotlinx.serialization.Serializable

@Serializable
data class AppInstallationResponse(val id: Long)

@Serializable
data class AccessTokenResponse(val token: String)
