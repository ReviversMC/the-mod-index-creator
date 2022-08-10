package com.github.reviversmc.themodindex.creator.maintainer.apicalls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppInstallationResponse(val id: Long)

@Serializable
data class AccessTokenResponse(val token: String)

@Serializable
data class RefBranchResponse(@SerialName("object") val refObject: RefObject)

@Serializable
data class RefObject(val sha: String)
