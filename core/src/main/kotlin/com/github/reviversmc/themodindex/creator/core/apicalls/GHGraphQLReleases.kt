package com.github.reviversmc.themodindex.creator.core.apicalls

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional

object GHGraphQLReleases {

    suspend fun obtainAllAssets(
        apolloClient: ApolloClient,
        repoOwner: String,
        repoName: String,
    ): List<String> {
        val urls = mutableListOf<String>()
        var releaseBeforeId: String? = null

        while (true) {
            val query =
                apolloClient.query(ReleaseAssetsQuery(repoOwner, repoName, Optional.presentIfNotNull(releaseBeforeId)))
                    .execute()
            releaseBeforeId = query.data?.repository?.releases?.pageInfo?.startCursor

            val downloadUrlsFromQuery = query.data?.repository?.releases?.nodes?.mapNotNull { node ->
                node?.releaseAssets?.nodes?.mapNotNull { it?.downloadUrl as String }
            }?.flatten()

            if (downloadUrlsFromQuery.isNullOrEmpty()) break
            else urls.addAll(downloadUrlsFromQuery)
        }

        return urls
    }

}