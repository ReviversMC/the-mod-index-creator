package com.github.reviversmc.themodindex.creator.ghapp

import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthApiCall
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GitHubComponent: KoinComponent {

    val indexApiDownloader by inject<ApiDownloader>()
    val modrinthApiCall by inject<ModrinthApiCall>()
}