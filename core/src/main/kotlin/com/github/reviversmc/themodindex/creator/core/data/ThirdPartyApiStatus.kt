package com.github.reviversmc.themodindex.creator.core.data

enum class ThirdPartyApiStatus {
    ALL_WORKING, CURSEFORGE_WORKING, GITHUB_WORKING, MODRINTH_WORKING, NONE_WORKING;

    companion object {
        fun isAllWorking(workingList: List<ThirdPartyApiStatus> = emptyList()) =
            CURSEFORGE_WORKING in workingList && GITHUB_WORKING in workingList && MODRINTH_WORKING in workingList

        fun isNoneWorking(workingList: List<ThirdPartyApiStatus> = emptyList()) =
            workingList.isEmpty() || workingList == listOf(NONE_WORKING)
    }
}