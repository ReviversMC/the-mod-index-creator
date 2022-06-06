package com.github.reviversmc.themodindex.creator.ghapp

import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthApiCall
import org.kohsuke.github.GitHub
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named

class GitHubComponent: KoinComponent {
    fun gitHubAppApi(jwt: String) = get<GitHub>(named("jwt")) { parametersOf(jwt) }
    fun gitHubInstallationApi(installationToken: String) = get<GitHub>(named("installation")) { parametersOf(installationToken) }
    val indexApiDownloader by inject<ApiDownloader>()
    val modrinthApiCall by inject<ModrinthApiCall>()
}