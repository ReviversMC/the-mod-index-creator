package com.github.reviversmc.themodindex.creator.core.apicalls

import com.apollographql.apollo3.ApolloClient

object GHGraphQLicense {

    suspend fun licenseSPDXId(apolloClient: ApolloClient, repoOwner: String, repoName: String) =
        apolloClient.query(LicenseIdQuery(repoOwner, repoName)).execute().data?.repository?.licenseInfo?.spdxId

}