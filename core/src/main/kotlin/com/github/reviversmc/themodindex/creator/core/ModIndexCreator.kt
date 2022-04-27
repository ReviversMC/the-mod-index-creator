package com.github.reviversmc.themodindex.creator.core

import com.github.reviversmc.themodindex.api.data.IndexJson
import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.apicalls.CurseForgeApiCall
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthResponse
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthV2ApiCall
import okhttp3.internal.toImmutableList
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

class ModIndexCreator(
    private val apiDownloader: ApiDownloader,
    private val curseForgeApiKey: String, //Should we make this nullable?
    private val modrinthApiCall: ModrinthV2ApiCall,
) : Creator {

    private val schemaVersion = "1.0.0"

    //Give manual control over the api key
    private val curseForgeApiCall: CurseForgeApiCall = Koin().get { parametersOf(curseForgeApiKey) }

    /**
     * Creates a map of [ManifestJson.ManifestFile]s on CurseForge for the mod, according to its mod loaders
     *
     * @param curseForgeId The CurseForge ID of the mod
     * @return A map of [ManifestJson.ManifestFile]s on CurseForge for the mod, according to its mod loaders
     * @author ReviversMC
     * @since 1.0.0-1.0.0
     * */
    private fun downloadCurseForgeFiles(curseForgeId: String): MutableMap<String, MutableList<ManifestJson.ManifestFile>> {

        val curseForgeMod = curseForgeApiCall.mod(curseForgeId) ?: return mutableMapOf()
        if (curseForgeMod.allowModDistribution == false) return mutableMapOf()

        val downloadFiles = mutableMapOf<String, MutableList<ManifestJson.ManifestFile>>()
        CurseForgeApiCall.ModLoaderType.values().forEach { loader ->
            val existingFiles = downloadFiles.getOrDefault(loader.name.lowercase(), mutableListOf())
            for (file in curseForgeApiCall.files(curseForgeId, loader)?.data ?: return mutableMapOf()) {
                existingFiles.add(
                    ManifestJson.ManifestFile(
                        file.displayName,
                        file.gameVersions.sortedDescending(),
                        file.hashes.filter { it.makeSenseOfAlgo().equals("sha1", true) }[0].value,
                        mutableListOf(file.downloadUrl)
                    )
                )
            }
            downloadFiles[loader.name.lowercase()] = existingFiles
        }
        return downloadFiles
    }

    //TODO some sort of github downloader

    /**
     * Creates a map of [ManifestJson.ManifestFile]s on Modrinth for the project, according to its mod loaders
     *
     * @param modrinthProject The Modrinth project for this project
     * @return A map of [ManifestJson.ManifestFile]s on Modrinth for the project, according to its mod loaders
     * @author ReviversMC
     * @since 1.0.0-1.0.0
     */
    private fun downloadModrinthFiles(modrinthProject: ModrinthResponse.ProjectResponse):
            MutableMap<String, MutableList<ManifestJson.ManifestFile>> {

        if (apiDownloader.getOrDownloadIndexJson()?.schemaVersion != schemaVersion) {
            throw IllegalStateException(
                "You are using an outdated version of this tool. Please update to the latest version." +
                        "Attempted index version to target: $schemaVersion,\n" +
                        "but found: ${apiDownloader.getOrDownloadIndexJson()?.schemaVersion}"
            )
        }


        val downloadFiles = mutableMapOf<String, MutableList<ManifestJson.ManifestFile>>()
        for (version in modrinthProject.versions) {
            val versionResponse = modrinthApiCall.version(version) ?: continue

            versionResponse.loaders.forEach { loader ->
                val existingFiles = downloadFiles.getOrDefault(loader.lowercase(), mutableListOf())
                versionResponse.files.forEach {
                    existingFiles.add(
                        ManifestJson.ManifestFile(
                            versionResponse.name,
                            versionResponse.gameVersions.sortedDescending(),
                            it.hashes.sha1,
                            mutableListOf(it.url)
                        )
                    )
                }
                downloadFiles[loader.lowercase()] = existingFiles
            }
        }
        return downloadFiles
    }

    override fun createManifest(modrinthId: String?, curseForgeId: String?): Map<String, ManifestJson> {
        modrinthId ?: curseForgeId ?: return emptyMap() //TODO: Support for non curse OR modrinth mods.

        val otherLinks = mutableListOf<ManifestJson.ManifestLinks.OtherLink>()
        val downloadFiles = mutableMapOf<String, MutableList<ManifestJson.ManifestFile>>()
        val returnMap = mutableMapOf<String, ManifestJson>()

        curseForgeId?.run {
            val files = downloadCurseForgeFiles(curseForgeId)
            downloadFiles.putAll(files)
            curseForgeApiCall.mod(curseForgeId)?.links?.let {
                otherLinks.add(ManifestJson.ManifestLinks.OtherLink("website", it.websiteUrl))
                otherLinks.add(ManifestJson.ManifestLinks.OtherLink("wiki", it.wikiUrl))
            }
        }

        if (modrinthId != null) {
            val modrinthProject = modrinthApiCall.project(modrinthId)?.let {
                val files = downloadModrinthFiles(it)
                downloadFiles.forEach { entry ->
                    downloadFiles[entry.key] =
                        entry.value.plus(files[entry.key] ?: mutableListOf()) as MutableList<ManifestJson.ManifestFile>
                }

                otherLinks.add(ManifestJson.ManifestLinks.OtherLink("discord", it.discordUrl))
                otherLinks.add(ManifestJson.ManifestLinks.OtherLink("wiki", it.wikiUrl))
                it.donationUrls.forEach { donationLink ->
                    otherLinks.add(ManifestJson.ManifestLinks.OtherLink(donationLink.platform, donationLink.url))
                }
                return@let it
            } ?: throw IllegalArgumentException("No modrinth project found for $modrinthId")

            downloadFiles.forEach { modLoader ->
                returnMap[modLoader.key] = ManifestJson(
                    schemaVersion, modrinthProject.title, modrinthApiCall.projectOwner(modrinthId),
                    modrinthProject.projectLicense?.id, curseForgeId, modrinthId,
                    ManifestJson.ManifestLinks(
                        modrinthProject.issuesUrl, modrinthProject.sourceUrl, otherLinks
                    ), modLoader.value
                )
            }
            return returnMap
        }

        curseForgeId?.run {
            curseForgeApiCall.mod(curseForgeId)?.let { mod ->
                downloadFiles.forEach { modLoader ->
                    returnMap[modLoader.key] = ManifestJson(
                        schemaVersion, mod.name, mod.authors[0].name, null, //TODO Get license from Source control
                        curseForgeId, null, //Modrinth id is known to be null, else it would have exited the func.
                        ManifestJson.ManifestLinks(
                            mod.links.issuesUrl, mod.links.sourceUrl, otherLinks
                        ), modLoader.value
                    )
                }
                return returnMap
            }
        }

        return emptyMap()
    }

    override fun modifyIndex(indexToModify: IndexJson, manifest: ManifestJson, genericIdentifier: String): IndexJson {
        if (indexToModify.schemaVersion != schemaVersion) {
            throw IllegalStateException(
                "You are using an outdated version of this tool. Please update to the latest version." +
                        "Attempted index version to target: $schemaVersion,\n" +
                        "but found: ${apiDownloader.getOrDownloadIndexJson()?.schemaVersion}"
            )
        }

        val indexFiles = indexToModify.files.toMutableList()

        for (manifestFile in manifest.files) {
            val indexFile = IndexJson.IndexFile(genericIdentifier, manifestFile.sha1Hash ?: continue)
            indexFiles.add(indexFile)
        }
        return indexToModify.copy(files = indexFiles.toImmutableList().sortedBy { it.identifier })

    }
}
