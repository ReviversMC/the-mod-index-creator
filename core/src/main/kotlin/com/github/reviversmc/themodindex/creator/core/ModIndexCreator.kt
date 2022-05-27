package com.github.reviversmc.themodindex.creator.core

import com.github.reviversmc.themodindex.api.data.IndexJson
import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.apicalls.CurseForgeApiCall
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthApiCall
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.OkHttpClient
import okhttp3.Request
import org.kohsuke.github.GitHub
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest

class ModIndexCreator(
    private val apiDownloader: ApiDownloader,
    private val curseApiKey: String,
    private val curseForgeApiCall: CurseForgeApiCall,
    private val githubApiCall: GitHub,
    private val modrinthApiCall: ModrinthApiCall,
    private val okHttpClient: OkHttpClient
) : Creator {

    private val indexVersion = "4.0.0"

    /**
     * Create a sha512 hash for a given input
     * @param input The bytes to create a sha512 hash for
     * @return The sha512 hash
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun createSHA512Hash(input: ByteArray): String {
        var out = BigInteger(
            1, MessageDigest.getInstance("SHA-512").digest(input)
        ).toString(16)

        while (out.length < 128) out = "0$out"
        return out
    }

    /**
     * Creates a map of [ManifestJson.ManifestFile]s on CurseForge for the mod, according to its mod loaders
     *
     * @param curseForgeId The CurseForge ID of the mod
     * @param existingFiles The existing files to add to, if any
     * @return A map of [ManifestJson.ManifestFile]s on CurseForge for the mod, according to its mod loaders
     * @author ReviversMC
     * @since 1.0.0
     * */
    private fun downloadCurseForgeFiles(
        curseForgeId: Int, existingFiles: Map<String, List<ManifestJson.ManifestFile>> = emptyMap()
    ): Map<String, List<ManifestJson.ManifestFile>> {

        /*
        Atm of creation, curse files cannot be included in the index.
        See https://media.discordapp.net/attachments/734077874708938867/971793259317854349/sconosciuto.jpeg (picture)
        or https://discord.com/channels/900128427150028811/940227856741597194/971451873074757712 (original source, CF dev server)
        */

        val returnMap = existingFiles.toMutableMap()
        for (modLoader in CurseForgeApiCall.ModLoaderType.values()) {
            if (modLoader == CurseForgeApiCall.ModLoaderType.ANY) continue
            val loaderFiles = returnMap.getOrDefault(modLoader.name.lowercase(), emptyList()).toMutableList()
            val loaderFileHashes = loaderFiles.map { it.sha512Hash.lowercase() }

            for (file in curseForgeApiCall.files(curseApiKey, curseForgeId, modLoader.curseNumber).body()?.data
                ?: continue) {

                val fileResponse =
                    okHttpClient.newCall(Request.Builder().url(file.downloadUrl ?: continue).build()).execute()
                val fileHash = createSHA512Hash(fileResponse.body?.bytes() ?: continue)
                fileResponse.close()

                if (loaderFileHashes.contains(fileHash)) {
                    loaderFileHashes.forEachIndexed { index, existingHash ->
                        if (existingHash.equals(fileHash, true)) loaderFiles[index] =
                            loaderFiles[index].copy(curseDownloadAvailable = true)
                    }
                } else loaderFiles.add(
                    ManifestJson.ManifestFile(
                        file.displayName, file.gameVersions.sortedDescending().mapNotNull {
                            try {
                                CurseForgeApiCall.ModLoaderType.valueOf(it.uppercase())
                                null//Do not add if detected to be loader
                            } catch (_: IllegalArgumentException) {
                                it
                            }
                        }, fileHash, emptyList(), true
                    )
                )

            }
            returnMap[modLoader.name.lowercase()] = loaderFiles.toList()
        }

        returnMap.values.removeIf { it.isEmpty() }
        return returnMap.toMap()
    }

    /**
     * Creates a map of [ManifestJson.ManifestFile]s on GitHub for the mod, according to its mod loaders.
     * THIS WILL ONLY ADD FILES FOR HASHES THAT ARE ALREADY PRESENT IN THE EXISTING FILES MAP.
     *
     * @param gitHubRepo The GitHub repository of the mod. Refers to the owner/repo format, not the https://github.com/owner/repo format.
     * @param existingFiles The existing files to add to, if any
     * @return A map of [ManifestJson.ManifestFile]s on GitHub for the mod, according to its mod loaders
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun downloadGitHubFiles(
        gitHubRepo: String, existingFiles: Map<String, List<ManifestJson.ManifestFile>>
    ): Map<String, List<ManifestJson.ManifestFile>> {

        val returnMap = existingFiles.toMutableMap()

        githubApiCall.getRepository(gitHubRepo).listReleases().forEach { release ->
            for (asset in release.listAssets()) {
                val response = okHttpClient.newCall(Request.Builder().url(asset.browserDownloadUrl).build()).execute()
                val fileHash = createSHA512Hash(response.body?.bytes() ?: continue)
                response.close()

                for ((loader, manifestFiles) in returnMap) {
                    if (!manifestFiles.map { it.sha512Hash }.contains(fileHash)) continue
                    manifestFiles.forEachIndexed { index, manifestFile ->
                        if (manifestFile.sha512Hash.equals(fileHash, true)) {
                            returnMap[loader] = manifestFiles.toMutableList().apply {
                                this[index] =
                                    manifestFile.copy(downloadUrls = this[index].downloadUrls + asset.browserDownloadUrl)
                            }
                            return@forEachIndexed
                        }
                    }
                }
            }
        }
        return returnMap.toMap()
    }

    /**
     * Creates a map of [ManifestJson.ManifestFile]s on Modrinth for the project, according to its mod loaders
     *
     * @param modrinthId The modrinth id for this project
     * @param existingFiles The existing files to add to, if any
     * @return A map of [ManifestJson.ManifestFile]s on Modrinth for the project, according to its mod loaders
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun downloadModrinthFiles(
        modrinthId: String, existingFiles: Map<String, List<ManifestJson.ManifestFile>> = emptyMap()
    ): Map<String, List<ManifestJson.ManifestFile>> {

        val returnMap = existingFiles.toMutableMap()
        //val downloadFiles = mutableMapOf<String, MutableList<ManifestJson.ManifestFile>>()
        modrinthApiCall.versions(modrinthId).body()?.forEach { versionResponse ->

            versionResponse.loaders.forEach { loader -> //All files here are guaranteed to work for the loader.
                val loaderFiles = returnMap.getOrDefault(loader.lowercase(), emptyList()).toMutableList()
                val loaderFileHashes = loaderFiles.map { it.sha512Hash.lowercase() }

                for (file in versionResponse.files) {

                    if (loaderFileHashes.contains(file.hashes.sha512)) {
                        loaderFileHashes.forEachIndexed { index, existingHash ->
                            if (existingHash.equals(file.hashes.sha512, true)) loaderFiles[index] =
                                loaderFiles[index].copy(downloadUrls = loaderFiles[index].downloadUrls + file.url)
                        } //TODO make everything enqueue
                    } else loaderFiles.add(
                        ManifestJson.ManifestFile(
                            versionResponse.name,
                            versionResponse.gameVersions.sortedDescending(),
                            file.hashes.sha512,
                            listOf(file.url),
                            false //We don't know if it's available, so assume false
                        )
                    )
                }
                returnMap[loader.lowercase()] = loaderFiles.toList()
            }
        }
        return returnMap.toMap()
    }

    @ExperimentalSerializationApi
    override fun createManifest(modrinthId: String?, curseForgeId: Int?): Map<String, ManifestJson> {

        if (apiDownloader.getOrDownloadIndexJson()?.indexVersion != indexVersion) {
            throw IllegalStateException(
                "Attempted index version to target: $indexVersion,\nbut found: ${apiDownloader.getOrDownloadIndexJson()?.indexVersion ?: "UNKNOWN"}"
            )
        }
        modrinthId ?: curseForgeId ?: return emptyMap() //TODO: Support for non curse OR modrinth mods.

        /*
        Current impl:
        - If modrinthId is not null, use modrinth for metadata
        - If curseForgeId is not null, use curseforge for metadata
        - If both are null, return empty map

        - If modrinthId is not null, use modrinth for files
        - If curseForgeId is not null, store a boolean in the manifest saying that curseforge files are available
        - If GitHub is used a source, scan GitHub releases for files. Check if the hash is similar to that provided by modrinth or curseforge
        - If all are null, don't include the version. If no versions are non-null, return empty map
         */

        val curseForgeMod = curseForgeId?.let { curseForgeApiCall.mod(curseApiKey, it) }?.body()
        val modrinthProject = modrinthId?.let { modrinthApiCall.project(it) }?.body()

        val gitHubUserRepo = modrinthProject?.sourceUrl?.let {//Make the source in the format of User/Repo
            val splitSource = it.split("/")
            return@let if (splitSource[2].equals("github.com", true)) "${splitSource[3]}/${splitSource[4]}" else null

        } ?: curseForgeMod?.data?.links?.sourceUrl?.let {
            val splitSource = it.split("/")
            return@let if (splitSource[2].equals("github.com", true)) "${splitSource[3]}/${splitSource[4]}" else null
        }

        val modrinthFiles = modrinthId?.let { downloadModrinthFiles(it) } ?: emptyMap()
        val curseAndModrinthFiles = curseForgeId?.let { downloadCurseForgeFiles(it, modrinthFiles) } ?: modrinthFiles

        val combinedFiles =
            gitHubUserRepo?.let { downloadGitHubFiles(it, curseAndModrinthFiles) } ?: curseAndModrinthFiles

        if (combinedFiles.isEmpty()) return emptyMap()

        val otherLinks = mutableListOf<ManifestJson.ManifestLinks.OtherLink>().apply {
            modrinthProject?.let {

                it.discordUrl?.let { url -> if (url != "") add(ManifestJson.ManifestLinks.OtherLink("discord", url)) }
                it.wikiUrl?.let { url -> if (url != "") add(ManifestJson.ManifestLinks.OtherLink("wiki", url)) }

                it.donationUrls?.forEach { donation ->
                    add(ManifestJson.ManifestLinks.OtherLink(donation.platform, donation.url))
                }
            }

            curseForgeMod?.data?.links?.let {
                if (it.websiteUrl != "") add(ManifestJson.ManifestLinks.OtherLink("website", it.websiteUrl))
                if (it.wikiUrl != "") add(ManifestJson.ManifestLinks.OtherLink("wiki", it.wikiUrl))
            }
        }.distinct()

        val returnMap = mutableMapOf<String, ManifestJson>()

        modrinthProject?.run {
            combinedFiles.forEach {
                returnMap[it.key] = ManifestJson(
                    indexVersion,
                    title,
                    modrinthApiCall.projectMembers(modrinthId).body()?.filter { member -> member.role == "Owner" }
                        ?.map { member -> member.userResponse.username }?.get(0)
                        ?: throw IOException("No owner found for modrinth project: $modrinthId"),
                    license?.id ?: "UNKNOWN",
                    //Could cause null to be wrapped in quotes, and we thus have to do the ugly check to prevent that.
                    curseForgeId,
                    id,
                    ManifestJson.ManifestLinks(issuesUrl, sourceUrl, otherLinks),
                    it.value
                )
            }
            return returnMap
        }

        curseForgeMod?.data?.run {
            combinedFiles.forEach {
                returnMap[it.key] = ManifestJson(
                    indexVersion,
                    name,
                    authors[0].name,
                    githubApiCall.getRepository(gitHubUserRepo).license.key,
                    curseForgeId,
                    modrinthId, //Modrinth id is known to be null, else it would have exited the func.
                    ManifestJson.ManifestLinks(
                        links.issuesUrl, links.sourceUrl, otherLinks
                    ),
                    it.value
                )
            }
            return returnMap
        }

        return emptyMap()
    }

    @ExperimentalSerializationApi
    override suspend fun createManifestAsync(modrinthId: String?, curseForgeId: Int?): Map<String, ManifestJson> {

    }

    override fun modifyIndex(
        indexToModify: IndexJson, manifest: ManifestJson, genericIdentifier: String
    ): IndexJson {
        if (indexToModify.indexVersion != indexVersion) {
            throw IllegalStateException(
                "Attempted index version to target: $indexVersion,\nbut found: ${indexToModify.indexVersion}"
            )
        }

        return indexToModify.copy(identifiers = indexToModify.identifiers.toMutableList().apply {
            manifest.files.forEach { add("$genericIdentifier:${it.sha512Hash}") }
        })
    }

    override suspend fun modifyIndexAsync(
        indexToModify: IndexJson,
        manifest: ManifestJson,
        genericIdentifier: String
    ): IndexJson {

    }
}
