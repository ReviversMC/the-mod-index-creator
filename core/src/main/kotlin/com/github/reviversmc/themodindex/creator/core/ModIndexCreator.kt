package com.github.reviversmc.themodindex.creator.core

import com.github.reviversmc.themodindex.api.data.ManifestJson
import com.github.reviversmc.themodindex.api.data.ManifestLinks
import com.github.reviversmc.themodindex.api.data.RelationsToOtherMods
import com.github.reviversmc.themodindex.api.data.VersionFile
import com.github.reviversmc.themodindex.api.downloader.ApiDownloader
import com.github.reviversmc.themodindex.creator.core.apicalls.*
import com.github.reviversmc.themodindex.creator.core.data.ManifestWithApiStatus
import com.github.reviversmc.themodindex.creator.core.data.ThirdPartyApiUsage
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.kohsuke.github.GitHub
import java.io.FileNotFoundException
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
    private val refreshGitHubClient: () -> GitHub,
    private val modrinthApiCall: ModrinthApiCall,
    private val okHttpClient: OkHttpClient,
) : Creator {

    private val indexVersion = "4.2.0"

    init {
        //Refresh every 50 minutes, not every hour, to account for delays
        timer("CreatorGitHubRefresh", true, 0, 1000 * 60 * 50) {
            githubClient = refreshGitHubClient()
        }
    }

    /**
     * Gets a GitHub client. This is unproven if it is valid. Use [validGitHubClient] instead to ensure credentials are valid.
     */
    private var githubClient = refreshGitHubClient()

    private fun validGitHubClient() = if (githubClient.isCredentialValid) githubClient else {
        githubClient = refreshGitHubClient()
        githubClient
    }

    /**
     * Creates a sha512 hash for the given [input] bytes
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
                this.length - 1,
                this.length
            )
        }

        return formattedString
    }

    // TODO Possibly fix file generation ranking snapshots above minecraft versions for all download/creation methods

    /**
     * Creates a [ManifestVersionsPerLoader] for the CurseForge mod, which is found using its [curseForgeId].
     * The results can be merged with any [existingFiles] that have already been generated.
     * @author ReviversMC
     * @since 1.0.0
     */
    private fun downloadCurseForgeFiles(
        curseForgeId: Int, existingFiles: ManifestVersionsPerLoader = emptyMap(),
    ): ManifestVersionsPerLoader = existingFiles.toMutableMap().apply {

        /*
        Atm of creation, curse files cannot be included in the index.
        See https://media.discordapp.net/attachments/734077874708938867/971793259317854349/sconosciuto.jpeg (picture)
        or https://discord.com/channels/900128427150028811/940227856741597194/971451873074757712 (original source, CF dev server)
        */

        for (modLoader in CurseForgeApiCall.ModLoaderType.values()) {
            if (modLoader == CurseForgeApiCall.ModLoaderType.ANY) continue
            val loaderFiles = this.getOrDefault(modLoader.name.lowercase(), emptyList()).toMutableList()
            val loaderFileHashes = loaderFiles.map { it.sha512Hash.lowercase() }

            try {
                val cfFiles = curseForgeApiCall.files(curseApiKey, curseForgeId, modLoader.curseNumber).execute()

                for (file in cfFiles.body()?.data ?: continue) {

                    val fileResponse =
                        okHttpClient.newCall(Request.Builder().url(file.downloadUrl ?: continue).build()).execute()
                    val fileHash = createSHA512Hash(fileResponse.body()?.bytes() ?: continue)
                    fileResponse.close()

                    fun obtainRelation(relationType: RelationType): List<String> = file.dependencies.filter {
                        relationType.curseNumber == it.relationType
                    }.mapNotNull { curseFileDependency ->
                        curseForgeApiCall.mod(curseApiKey, curseFileDependency.modId).execute()
                            .body()?.data?.slug?.formatRightGenericIdentifier()?.let {

                                if (curseForgeApiCall.files(
                                        curseApiKey, curseFileDependency.modId, modLoader.curseNumber
                                    ).execute().body()?.data?.isNotEmpty() == true
                                ) {
                                    "${modLoader.name.lowercase()}:$it"

                                } else if (modLoader == CurseForgeApiCall.ModLoaderType.QUILT && curseForgeApiCall.files(
                                        curseApiKey,
                                        curseFileDependency.modId,
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
    private fun downloadGitHubFiles(
        gitHubRepo: String, existingFiles: ManifestVersionsPerLoader = emptyMap(),
    ): ManifestVersionsPerLoader = existingFiles.toMutableMap().apply {

        try {
            validGitHubClient().getRepository(gitHubRepo)?.listReleases()?.forEach { release ->
                try {
                    for (asset in release?.listAssets() ?: emptyList()) {
                        val response =
                            okHttpClient.newCall(Request.Builder().url(asset.browserDownloadUrl).build()).execute()
                        val fileHash = createSHA512Hash(response.body()?.bytes() ?: continue)
                        response.close()

                        for ((loader, manifestFiles) in this) {
                            if (!manifestFiles.map { it.sha512Hash }.contains(fileHash)) continue
                            manifestFiles.forEachIndexed { index, manifestFile ->
                                if (manifestFile.sha512Hash.equals(fileHash, true)) {
                                    this[loader] = manifestFiles.toMutableList().also { files ->
                                        files[index] =
                                            manifestFile.copy(downloadUrls = files[index].downloadUrls + asset.browserDownloadUrl)
                                    }.toList().sortedByDescending { it.mcVersions.firstOrNull() }
                                    return@forEachIndexed // There shouldn't be two files of the same hash, so we can safely leave the loop.
                                }
                            }
                        }
                    }
                } catch (ex: SocketTimeoutException) {
                    // Do nothing, we don't have the files
                }
            }
        } catch (ex: FileNotFoundException) {
            // This means that the repo doesn't exist
        }
    }.toMap()


    /**
     * Creates a [ManifestVersionsPerLoader] for the Modrinth project, which is found using [modrinthId].
     * The results can be merged with any [existingFiles] that have already been generated.
     * @author ReviversMC
     * @since 1.0.0
     */
    private suspend fun downloadModrinthFiles(
        modrinthId: String,
        existingFiles: ManifestVersionsPerLoader = emptyMap(),
    ): ManifestVersionsPerLoader = existingFiles.toMutableMap().apply {

        modrinthApiCall.versions(modrinthId).execute().run {
            if (body() != null) body() else {
                delay(((headers()["x-ratelimit-reset"]?.toLong() ?: -1) + 1) * 1000)
                modrinthApiCall.versions(modrinthId).execute().body()
            }
        }?.forEach { versionResponse ->

            versionResponse.loaders.forEach { loader -> // All files here are guaranteed to work for the loader.
                val loaderFiles = this.getOrDefault(loader.lowercase(), emptyList()).toMutableList()
                val loaderFileHashes = loaderFiles.map { it.sha512Hash.lowercase() }

                for (file in versionResponse.files) {

                    fun obtainRelation(dependencyType: ModrinthDependencyType): List<String> {
                        val projectIdDependencies =
                            versionResponse.dependencies.filter { dependencyType.modrinthString == it.dependencyType && it.projectId != null }
                                .mapNotNull { it.projectId }

                        val versionIdDependencies =
                            versionResponse.dependencies.filter { dependencyType.modrinthString == it.dependencyType && it.projectId == null && it.versionId != null }

                        return projectIdDependencies.mapNotNull { projectId ->
                            modrinthApiCall.project(projectId).execute().body()?.slug?.formatRightGenericIdentifier()
                                ?.let {
                                    if (modrinthApiCall.versions(projectId, "[\"$loader\"]").execute().body()
                                            ?.isNotEmpty() == true
                                    ) {
                                        "$loader:$it"

                                    } else if (loader == "quilt" && modrinthApiCall.versions(
                                            projectId, "[\"fabric\"]"
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

                    if (loaderFileHashes.contains(file.hashes.sha512)) {
                        loaderFileHashes.forEachIndexed { index, existingHash ->
                            if (existingHash.equals(file.hashes.sha512, true)) loaderFiles[index] =
                                loaderFiles[index].copy(downloadUrls = loaderFiles[index].downloadUrls + file.url)
                        }
                    } else loaderFiles.add(
                        VersionFile(
                            versionResponse.name,
                            versionResponse.gameVersions.sortedDescending(),
                            file.hashes.sha512,
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
    ): ManifestWithApiStatus = createManifest(modrinthId, curseForgeId, preferCurseForgeData)

    override suspend fun createManifestModrinth(
        modrinthId: String,
        curseForgeId: Int?,
        preferModrinthData: Boolean,
    ): ManifestWithApiStatus = createManifest(
        modrinthId, curseForgeId, !preferModrinthData /* Invert the preference, as the param asks for the opposite */
    )


    private suspend fun createManifest(
        modrinthId: String?,
        curseForgeId: Int?,
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
        modrinthId ?: curseForgeId ?: throw IllegalArgumentException("Both the CurseForge and Modrinth id are null!")

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

        val curseForgeMod = curseForgeId?.let {
            try {
                curseForgeApiCall.mod(curseApiKey, it).execute().body()
            } catch (ex: SocketTimeoutException) {
                null
            }
        }
        val modrinthProject = modrinthId?.let {
            modrinthApiCall.project(it).execute().run {
                if (body() != null) body() else {
                    delay(((headers()["x-ratelimit-reset"]?.toLong() ?: -1) + 1) * 1000)
                    modrinthApiCall.project(it).execute().body()
                }
            }
        }

        fun obtainGitHubFromCurse() = curseForgeMod?.data?.links?.sourceUrl?.let {
            val splitSource = it.split("/")
            return@let if (splitSource[2].equals("github.com", true)) "${splitSource[3]}/${splitSource[4]}" else null
        }

        fun obtainGitHubFromModrinth() = modrinthProject?.sourceUrl?.let {// Make the source in the format of User/Repo
            val splitSource = it.split("/")
            return@let if (splitSource[2].equals("github.com", true)) "${splitSource[3]}/${splitSource[4]}" else null

        }

        val gitHubUserRepo = if (preferCurseOverModrinth) obtainGitHubFromCurse() ?: obtainGitHubFromModrinth()
        else obtainGitHubFromModrinth() ?: obtainGitHubFromCurse()


        val usedThirdPartyApis = mutableListOf<ThirdPartyApiUsage>()


        val curseAndModrinthFiles = if (preferCurseOverModrinth) {
            val curseFiles = curseForgeId?.let { downloadCurseForgeFiles(it) }?.also {
                usedThirdPartyApis.add(ThirdPartyApiUsage.CURSEFORGE_USED)
            } ?: emptyMap()

            modrinthId?.let { downloadModrinthFiles(it, curseFiles) }?.also {
                usedThirdPartyApis.add(ThirdPartyApiUsage.MODRINTH_USED)
            } ?: curseFiles
        } else {
            val modrinthFiles = modrinthId?.let { downloadModrinthFiles(it) }?.also {
                usedThirdPartyApis.add(ThirdPartyApiUsage.MODRINTH_USED)
            } ?: emptyMap()

            curseForgeId?.let { downloadCurseForgeFiles(it, modrinthFiles) }?.also {
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

            curseForgeMod?.data?.links?.let { modLinks ->
                if (modLinks.websiteUrl != "") add(ManifestLinks.OtherLink("website", modLinks.websiteUrl))
                modLinks.wikiUrl?.let { if (it != "") add(ManifestLinks.OtherLink("wiki", it)) }
            }
        }.distinct()

        val returnManifests = mutableListOf<ManifestJson>().apply {

            fun curseForgeToManifest() = curseForgeMod?.data?.let { modData ->
                combinedFiles.forEach { (modLoader, manifestFiles) ->
                    add(ManifestJson(indexVersion,
                        "${modLoader}:${modData.slug.formatRightGenericIdentifier()}",
                        modData.name,
                        modData.authors.firstOrNull()?.name ?: "UNKNOWN",
                        gitHubUserRepo?.let {
                            try {
                                validGitHubClient().getRepository(it).license?.key
                            } catch (ex: FileNotFoundException) {
                                null
                            }
                        }
                            ?: modrinthProject?.license?.id,
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

            suspend fun modrinthToManifest(curseData: CurseModData?) =
                modrinthProject?.let { _ -> // No better alias for this
                    combinedFiles.forEach { (modLoader, manifestFiles) ->
                        add(ManifestJson(indexVersion,
                            "$modLoader:${modrinthProject.slug.formatRightGenericIdentifier()}",
                            modrinthProject.title,
                            modrinthApiCall.projectMembers(modrinthId).execute().run {
                                if (body() != null) body() else {
                                    delay(((headers()["x-ratelimit-reset"]?.toLong() ?: -1) + 1) * 1000)
                                    modrinthApiCall.projectMembers(modrinthId).execute().body()
                                }
                            }?.first { member -> member.role == "Owner" }?.userResponse?.username
                                ?: curseData?.authors?.firstOrNull()?.name ?: "UNKNOWN",
                            modrinthProject.license?.id
                                ?: gitHubUserRepo?.let {
                                    try {
                                        validGitHubClient().getRepository(it).license?.key
                                    } catch (ex: FileNotFoundException) {
                                        null
                                    }
                                },
                            curseData?.id,
                            modrinthProject.id,
                            ManifestLinks(
                                modrinthProject.issuesUrl ?: curseData?.links?.issuesUrl,
                                modrinthProject.sourceUrl ?: curseData?.links?.sourceUrl,
                                otherLinks
                            ),
                            manifestFiles.sortedByDescending { it.mcVersions.firstOrNull() })
                        )
                    }
                    return true
                } ?: false

            if (preferCurseOverModrinth) {
                if (!curseForgeToManifest()) modrinthToManifest(curseForgeMod?.data)
            } else {
                if (!modrinthToManifest(curseForgeMod?.data)) curseForgeToManifest()
            }

        }.toList()

        return ManifestWithApiStatus(
            usedThirdPartyApis.toList(),
            returnManifests // returnManifests may be empty here, if no files have been added.
        )
    }
}