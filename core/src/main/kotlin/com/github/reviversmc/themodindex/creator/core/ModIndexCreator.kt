package com.github.reviversmc.themodindex.creator.core

import com.apollographql.apollo3.ApolloClient
import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.data.ManifestLinks
import com.github.reviversmc.themodindex.api.data.RelationsToOtherMods
import com.github.reviversmc.themodindex.api.data.VersionFile
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.apicalls.*
import com.github.reviversmc.themodindex.creator.core.data.ManifestWithApiStatus
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigInteger
import java.net.SocketTimeoutException
import java.security.MessageDigest
import kotlin.concurrent.timer

/**
 * Helps to clarify what the underlying [String] stands for.
 * @author ReviversMC
 * @since 1.0.0
 */
private typealias ModLoaderType = String

/**
 * Helps to clarify what the underlying [Map] stands for.
 * @author ReviversMC
 * @since 1.0.0
 */
private typealias ManifestVersionsPerLoader = Map<ModLoaderType, List<VersionFile>>

class ModIndexCreator(
    private val apiDownloader: ApiDownloader,
    private val curseApiKey: String,
    private val curseForgeApiCall: CurseForgeApiCall,
    private val refreshGitHubClient: () -> ApolloClient,
    private val modrinthApiCall: ModrinthApiCall,
    private val okHttpClient: OkHttpClient,
) : Creator {

    private val indexVersion = "5.0.0"

    /**
     * Gets the [ApolloClient] used to connect to GitHub
     * @author ReviversMC
     * @since 1.0.0
     */
    private var githubClient = refreshGitHubClient()

    init {
        // Refresh every 50 minutes, not every hour, to account for delays
        timer("CreatorGitHubRefresh", true, 50L * 60L * 1000L, 50L * 60L * 1000L) {
            githubClient = refreshGitHubClient()
        }
    }

    /**
     * Creates a sha512 hash for the given [input] bytes
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun createShortSHA512Hash(input: ByteArray): String {
        var out = BigInteger(
            1, MessageDigest.getInstance("SHA-512").digest(input)
        ).toString(16)

        while (out.length < 128) out = "0$out"
        return out.substring(0, 15)
    }

    /**
     * Formats a string to be used as the right half of a generic identifier
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun String.formatRightGenericIdentifier(): String {
        val regex = Regex("^[a-z0-9\\-_]$")
        val lowercaseCandidate = this.lowercase()
        val formattedString = buildString {
            for (char in lowercaseCandidate) {
                if (char == ' ') append('_')
                else if (regex.matches(char.toString())) append(char)
                // else just ignore (i.e. append('') )
            }

            while (!Regex("^[a-z0-9]$").matches(this[0].toString())) this.delete(0, 1)
            while (!Regex("^[a-z0-9]$").matches(this[this.length - 1].toString())) this.delete(
                this.length - 1, this.length
            )
        }

        return formattedString
    }

    // TODO Possibly fix file generation ranking snapshots above minecraft versions for all download/creation methods

    /**
     * Creates a [ManifestVersionsPerLoader] for the CurseForge mod, which is found using its [curseForgeMod].
     * The results can be merged with any [existingFiles] that have already been generated.
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun downloadCurseForgeFiles(
        curseForgeMod: CurseModData, existingFiles: ManifestVersionsPerLoader = emptyMap(),
    ): ManifestVersionsPerLoader = existingFiles.toMutableMap().apply {

        /*
        Atm of creation, curse files cannot be included in the index.
        See https://media.discordapp.net/attachments/734077874708938867/971793259317854349/sconosciuto.jpeg (picture)
        or https://discord.com/channels/900128427150028811/940227856741597194/971451873074757712 (original source, CF dev server)
        */

        for (modLoader in CurseForgeApiCall.ModLoaderType.values()) {
            if (modLoader == CurseForgeApiCall.ModLoaderType.ANY) continue
            val loaderFiles = this.getOrDefault(modLoader.name.lowercase(), emptyList()).toMutableList()
            val loaderFileHashes = loaderFiles.map { it.shortSha512Hash.lowercase() }

            try {
                val cfFiles = curseForgeApiCall.files(curseApiKey, curseForgeMod.id, modLoader.curseNumber).execute()

                for (file in cfFiles.body()?.data ?: continue) {

                    val fileResponse =
                        okHttpClient.newCall(Request.Builder().url(file.downloadUrl ?: continue).build()).execute()
                    val fileHash = createShortSHA512Hash(fileResponse.body?.bytes() ?: continue)
                    fileResponse.close()

                    fun obtainRelation(relationType: RelationType) = file.dependencies.filter {
                        relationType.curseNumber == it.relationType
                    }.mapNotNull { curseDependency ->
                        curseForgeApiCall.mod(curseApiKey, curseDependency.modId).execute()
                            .body()?.data?.slug?.formatRightGenericIdentifier()?.let {

                                if (curseForgeApiCall.files(
                                        curseApiKey, curseDependency.modId, modLoader.curseNumber
                                    ).execute().body()?.data?.isNotEmpty() == true
                                ) {
                                    "${modLoader.name.lowercase()}:$it"

                                } else if (modLoader == CurseForgeApiCall.ModLoaderType.QUILT && curseForgeApiCall.files(
                                        curseApiKey,
                                        curseDependency.modId,
                                        CurseForgeApiCall.ModLoaderType.FABRIC.curseNumber
                                    ).execute().body()?.data?.isNotEmpty() == true
                                ) {
                                    // Special concession for Quilt, where we will also check for Fabric files
                                    "${CurseForgeApiCall.ModLoaderType.FABRIC.name.lowercase()}:$it"

                                } else null// If we can't find an appropriate file, don't add the dependency

                            }
                    }


                    if (loaderFileHashes.contains(fileHash)) {
                        loaderFileHashes.forEachIndexed { index, existingHash ->
                            if (existingHash.equals(fileHash, true)) loaderFiles[index] =
                                loaderFiles[index].copy(curseDownloadAvailable = true)
                        }
                    } else loaderFiles.add(
                        VersionFile(
                            file.displayName, file.gameVersions.sortedDescending().mapNotNull {
                                try {
                                    CurseForgeApiCall.ModLoaderType.valueOf(it.uppercase())
                                    null// Do not add if detected to be loader
                                } catch (_: IllegalArgumentException) {
                                    it
                                }
                            }, fileHash, emptyList(), true, RelationsToOtherMods(
                                obtainRelation(RelationType.REQUIRED_DEPENDENCY),
                                obtainRelation(RelationType.INCOMPATIBLE)
                            )
                        )
                    )

                }
            } catch (ex: SocketTimeoutException) {
                // Do nothing, we don't have the files
            }
            this[modLoader.name.lowercase()] = loaderFiles.toList().sortedByDescending { it.mcVersions.firstOrNull() }
        }

        this.values.removeIf { it.isEmpty() }
    }.toMap()

    /**
     * Creates a [ManifestVersionsPerLoader] for the GitHub mod, found using its [gitHubRepo].
     * [gitHubRepo] is the owner/repo format, not the https://github.com/owner/repo format.
     *
     * THIS WILL ONLY ADD FILES FOR HASHES THAT ARE ALREADY PRESENT IN THE EXISTING FILES MAP.
     *
     * The results can be merged with any [existingFiles] that have already been generated.
     *
     * @author ReviversMC
     * @since 1.0.0
     */
    private suspend fun downloadGitHubFiles(
        gitHubRepo: String, existingFiles: ManifestVersionsPerLoader = emptyMap(),
    ): ManifestVersionsPerLoader = existingFiles.toMutableMap().apply {

        for (asset in GHGraphQLReleases.obtainAllAssets(
            githubClient,
            gitHubRepo.substringBefore("/"),
            gitHubRepo.substringAfter("/")
        )) {
            val response =
                okHttpClient.newCall(Request.Builder().url(asset).build()).execute()
            val fileHash = createShortSHA512Hash(response.body?.bytes() ?: continue)
            response.close()

            for ((loader, manifestFiles) in this) {
                if (!manifestFiles.map { it.shortSha512Hash }.contains(fileHash)) continue
                manifestFiles.forEachIndexed { index, manifestFile ->
                    if (manifestFile.shortSha512Hash.equals(fileHash, true)) {
                        this[loader] = manifestFiles.toMutableList().also { files ->
                            files[index] =
                                manifestFile.copy(downloadUrls = files[index].downloadUrls + asset)
                        }.toList().sortedByDescending { it.mcVersions.firstOrNull() }
                        return@forEachIndexed // There shouldn't be two files of the same hash, so we can safely leave the loop.
                    }
                }
            }
        }
    }.toMap()


    /**
     * Creates a [ManifestVersionsPerLoader] for the Modrinth project, which is found using [modrinthProject].
     * The results can be merged with any [existingFiles] that have already been generated.
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun downloadModrinthFiles(
        modrinthProject: ModrinthProjectResponse,
        existingFiles: ManifestVersionsPerLoader = emptyMap(),
    ): ManifestVersionsPerLoader = existingFiles.toMutableMap().apply {

        modrinthApiCall.versions(modrinthProject.id).execute().body()?.forEach { versionResponse ->

            versionResponse.loaders.forEach { loader -> // All files here are guaranteed to work for the loader.
                val loaderFiles = this.getOrDefault(loader.lowercase(), emptyList()).toMutableList()
                val loaderFileHashes = loaderFiles.map { it.shortSha512Hash.lowercase() }

                for (file in versionResponse.files) {

                    fun obtainRelation(dependencyType: ModrinthDependencyType): List<String> {
                        val projectIdDependencies =
                            versionResponse.dependencies.filter { dependencyType.modrinthString == it.dependencyType && it.projectId != null }
                                .mapNotNull { it.projectId }

                        val versionIdDependencies =
                            versionResponse.dependencies.filter { dependencyType.modrinthString == it.dependencyType && it.projectId == null && it.versionId != null }

                        return projectIdDependencies.mapNotNull { dependencyId ->
                            modrinthApiCall.project(dependencyId).execute().body()?.slug?.formatRightGenericIdentifier()
                                ?.let {
                                    if (modrinthApiCall.versions(dependencyId, "[\"$loader\"]").execute().body()
                                            ?.isNotEmpty() == true
                                    ) {
                                        "$loader:$it"

                                    } else if (loader == "quilt" && modrinthApiCall.versions(
                                            dependencyId, "[\"fabric\"]"
                                        ).execute().body()?.isNotEmpty() == true
                                    ) {
                                        // Special concession for Quilt, where we will also check for Fabric files
                                        "fabric:$it"
                                    } else null// If we can't find an appropriate file, don't add the dependency
                                }
                        } + versionIdDependencies.mapNotNull { modrinthDependency ->
                            modrinthDependency.versionId?.let { versionId ->
                                modrinthApiCall.version(versionId).execute().body()?.let { version ->
                                    if (loader in version.loaders) {
                                        modrinthApiCall.project(version.projectId).execute()
                                            .body()?.slug?.formatRightGenericIdentifier()?.let { "$loader:$it" }

                                    } else if (loader == "quilt" && "fabric" in version.loaders) {
                                        modrinthApiCall.project(version.projectId).execute()
                                            .body()?.slug?.formatRightGenericIdentifier()?.let { "fabric:$it" }
                                    } else null
                                }
                            }
                        }
                    }

                    if (loaderFileHashes.contains(file.hashes.sha512.substring(0, 15))) {
                        loaderFileHashes.forEachIndexed { index, existingHash ->
                            if (existingHash.equals(file.hashes.sha512.substring(0, 15), true)) loaderFiles[index] =
                                loaderFiles[index].copy(downloadUrls = loaderFiles[index].downloadUrls + file.url)
                        }
                    } else loaderFiles.add(
                        VersionFile(
                            versionResponse.name,
                            versionResponse.gameVersions.sortedDescending(),
                            file.hashes.sha512.substring(0, 15),
                            listOf(file.url),
                            false, // We don't know if it's available, so assume false
                            RelationsToOtherMods(
                                obtainRelation(ModrinthDependencyType.REQUIRED),
                                obtainRelation(ModrinthDependencyType.INCOMPATIBLE)
                            )
                        )
                    )
                }
                this[loader.lowercase()] = loaderFiles.toList().sortedByDescending { it.mcVersions.firstOrNull() }
            }
        }
    }.toMap()

    override suspend fun createManifestCurseForge(
        curseForgeId: Int,
        modrinthId: String?,
        preferCurseForgeData: Boolean,
    ) = createManifest(
        modrinthId, curseForgeApiCall.mod(curseApiKey, curseForgeId).execute().body()?.data, preferCurseForgeData
    )

    override suspend fun createManifestCurseForge(
        curseForgeMod: CurseModData,
        modrinthId: String?,
        preferCurseForgeData: Boolean,
    ) = createManifest(modrinthId, curseForgeMod, preferCurseForgeData)

    override suspend fun createManifestModrinth(
        modrinthId: String,
        curseForgeId: Int?,
        preferModrinthData: Boolean,
    ) = if (curseForgeId != null) {
        // Invert the preference for [preferModrinthData], as the param in the actual method asks for the opposite
        createManifest(
            modrinthId, curseForgeApiCall.mod(curseApiKey, curseForgeId).execute().body()?.data, !preferModrinthData
        )
    } else createManifest(modrinthId, null, !preferModrinthData)


    override suspend fun createManifestModrinth(
        modrinthId: String,
        curseForgeMod: CurseModData?,
        preferModrinthData: Boolean,
    ) = createManifest(
        modrinthId, curseForgeMod, !preferModrinthData /* Invert the preference, as the param asks for the opposite */
    )


    private suspend fun createManifest(
        modrinthId: String?,
        curseForgeMod: CurseModData?,
        preferCurseOverModrinth: Boolean,
    ): ManifestWithApiStatus {

        if (apiDownloader.getOrDownloadIndexJson()?.indexVersion != indexVersion) {
            throw IllegalArgumentException(
                "Attempted index version to target: $indexVersion,\nbut found: ${apiDownloader.getOrDownloadIndexJson()?.indexVersion ?: "UNKNOWN"}"
            )
        }

        /*
        Should never happen in Kotlin, as the only publicly exposed methods that call this have at least one of the following as non null.
        I guess that this COULD happen if the methods were called from Java instead of Kotlin, as Java doesn't respect nullability.
        TODO: Support for non curse OR modrinth mods.
        */
        modrinthId ?: curseForgeMod ?: throw IllegalArgumentException("Both the CurseForge and Modrinth id are null!")

        /*
        Current impl:
        - If modrinthId is not null, use modrinth for metadata
        - If curseForgeId is not null, use curseforge for metadata
        - If both are null, return empty list

        - If modrinthId is not null, use modrinth for files
        - If curseForgeId is not null, store a boolean in the manifest saying that curseforge files are available
        - If GitHub is used a source, scan GitHub releases for files. Check if the hash is similar to that provided by modrinth or curseforge
        - If all are null, don't include the version. If no versions are non-null, return empty list
         */

        val modrinthProject = modrinthId?.let { modrinthApiCall.project(it).execute().body() }

        fun obtainGitHubFromCurse() = curseForgeMod?.links?.sourceUrl?.let {
            val splitSource = it.split("/")
            return@let if (splitSource.size < 5) null else {
                if (splitSource[2].equals("github.com", true)) "${splitSource[3]}/${splitSource[4]}" else null
            }
        }

        fun obtainGitHubFromModrinth() = modrinthProject?.sourceUrl?.let {// Make the source in the format of User/Repo
            val splitSource = it.split("/")
            return@let if (splitSource.size < 5) null else {
                if (splitSource[2].equals("github.com", true)) "${splitSource[3]}/${splitSource[4]}" else null
            }
        }

        val gitHubUserRepo = if (preferCurseOverModrinth) obtainGitHubFromCurse() ?: obtainGitHubFromModrinth()
        else obtainGitHubFromModrinth() ?: obtainGitHubFromCurse()


        val usedThirdPartyApis = mutableListOf<ThirdPartyApiUsage>()


        val curseAndModrinthFiles = if (preferCurseOverModrinth) {
            val curseFiles = curseForgeMod?.let { downloadCurseForgeFiles(it) }?.also {
                usedThirdPartyApis.add(ThirdPartyApiUsage.CURSEFORGE_USED)
            } ?: emptyMap()

            modrinthProject?.let { downloadModrinthFiles(it, curseFiles) }?.also {
                usedThirdPartyApis.add(ThirdPartyApiUsage.MODRINTH_USED)
            } ?: curseFiles
        } else {
            val modrinthFiles = modrinthProject?.let { downloadModrinthFiles(it) }?.also {
                usedThirdPartyApis.add(ThirdPartyApiUsage.MODRINTH_USED)
            } ?: emptyMap()

            curseForgeMod?.let { downloadCurseForgeFiles(it, modrinthFiles) }?.also {
                usedThirdPartyApis.add(ThirdPartyApiUsage.CURSEFORGE_USED)
            } ?: modrinthFiles
        }

        val combinedFiles = gitHubUserRepo?.let {
            try {
                downloadGitHubFiles(it, curseAndModrinthFiles)
            } catch (ex: IllegalArgumentException) {
                // This is most likely a GitHub user repo fail
                println(
                    """Failed to download files from GitHub:
                |GitHub User Repo: $gitHubUserRepo
                |
                |${ex.stackTraceToString()} """.trimMargin()
                )
                null
            }
        }?.also { usedThirdPartyApis.add(ThirdPartyApiUsage.GITHUB_USED) } ?: curseAndModrinthFiles

        if (ThirdPartyApiUsage.isAllWorking(usedThirdPartyApis)) usedThirdPartyApis.apply {
            clear()
            add(ThirdPartyApiUsage.ALL_USED)
        } else if (ThirdPartyApiUsage.isNoneWorking(usedThirdPartyApis)) usedThirdPartyApis.apply {
            clear()
            add(ThirdPartyApiUsage.NONE_USED)
        }


        if (combinedFiles.isEmpty()) return ManifestWithApiStatus(usedThirdPartyApis.toList(), emptyList())


        val otherLinks = mutableListOf<ManifestLinks.OtherLink>().apply {
            modrinthProject?.let {

                it.discordUrl?.let { url ->
                    if (url != "") add(
                        ManifestLinks.OtherLink(
                            "discord", url
                        )
                    )
                }
                it.wikiUrl?.let { url -> if (url != "") add(ManifestLinks.OtherLink("wiki", url)) }

                it.donationUrls?.forEach { donation ->
                    add(ManifestLinks.OtherLink(donation.platform, donation.url))
                }
            }

            curseForgeMod?.links?.let { modLinks ->
                if (modLinks.websiteUrl != "") add(ManifestLinks.OtherLink("website", modLinks.websiteUrl))
                modLinks.wikiUrl?.let { if (it != "") add(ManifestLinks.OtherLink("wiki", it)) }
            }
        }.distinct()

        val returnManifests = mutableListOf<ManifestJson>().apply {

            suspend fun curseForgeToManifest() = curseForgeMod?.let { modData ->
                combinedFiles.forEach { (modLoader, manifestFiles) ->
                    add(ManifestJson(indexVersion,
                        "${modLoader}:${modData.slug.formatRightGenericIdentifier()}",
                        modData.name,
                        modData.authors.firstOrNull()?.name ?: "UNKNOWN",
                        gitHubUserRepo?.let {
                            GHGraphQLicense.licenseSPDXId(
                                githubClient,
                                it.substringBefore("/"),
                                it.substringAfter("/")
                            )?.lowercase()
                        } ?: modrinthProject?.license?.id?.lowercase(),
                        modData.id,
                        modrinthProject?.id, // Modrinth id is known to be null, else it would have exited the func.
                        ManifestLinks(
                            modData.links.issuesUrl ?: modrinthProject?.issuesUrl,
                            modData.links.sourceUrl ?: modrinthProject?.sourceUrl,
                            otherLinks
                        ),
                        manifestFiles.sortedByDescending { it.mcVersions.firstOrNull() })
                    )
                }
                return true
            } ?: false

            suspend fun modrinthToManifest() =
                modrinthProject?.let { modrinthData ->
                    combinedFiles.forEach { (modLoader, manifestFiles) ->
                        add(
                            ManifestJson(indexVersion,
                                "$modLoader:${modrinthData.slug.formatRightGenericIdentifier()}",
                                modrinthData.title,
                                modrinthApiCall.projectMembers(modrinthId).execute().body()
                                    ?.firstOrNull { member -> member.role == "Owner" }?.userResponse?.username
                                    ?: curseForgeMod?.authors?.firstOrNull()?.name ?: "UNKNOWN",
                                gitHubUserRepo?.let {
                                    GHGraphQLicense.licenseSPDXId(
                                        githubClient,
                                        it.substringBefore("/"),
                                        it.substringAfter("/")
                                    )?.lowercase()
                                } ?: modrinthData.license?.id?.lowercase(),
                                curseForgeMod?.id,
                                modrinthData.id,
                                ManifestLinks(
                                    modrinthData.issuesUrl ?: curseForgeMod?.links?.issuesUrl,
                                    modrinthData.sourceUrl ?: curseForgeMod?.links?.sourceUrl,
                                    otherLinks
                                ),
                                manifestFiles.sortedByDescending { it.mcVersions.firstOrNull() })
                        )
                    }
                    return true
                } ?: false

            if (preferCurseOverModrinth) {
                if (!curseForgeToManifest()) modrinthToManifest()
            } else {
                if (!modrinthToManifest()) curseForgeToManifest()
            }

        }.toList()

        return ManifestWithApiStatus(
            usedThirdPartyApis.toList(),
            returnManifests // returnManifests may be empty here, if no files have been added.
        )
    }
}