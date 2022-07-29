import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import com.github.reviversmc.themodindex.creator.core.apicalls.CurseForgeApiCall
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthApiCall
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

private class GitHubAuthInterceptor(private val gitHubToken: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().addHeader("Authorization", "bearer $gitHubToken").build()
        return chain.proceed(request)
    }
}

val fakeApiCallModule = module {
    factory { (baseUrl: String) ->
        Retrofit.Builder()
            .addConverterFactory(get())
            .baseUrl(baseUrl)
            .client(get())
            .build()
            .create(CurseForgeApiCall::class.java)
    } bind CurseForgeApiCall::class

    factory { (baseUrl: String) ->
        Retrofit.Builder()
            .addConverterFactory(get())
            .baseUrl(baseUrl)
            .client(get())
            .build()
            .create(ModrinthApiCall::class.java)
    } bind ModrinthApiCall::class

    factory(named("githubGraphql")) { (githubToken: String) -> // We are unable to use single, because we need to inject the token, which may be different for each instance
        OkHttpClient.Builder().addInterceptor(
            GitHubAuthInterceptor(
                githubToken
            )
        ).protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE, Protocol.HTTP_2, Protocol.HTTP_1_1)).build()
    }

    factory { (githubToken: String) ->
        ApolloClient.Builder().serverUrl("https://api.github.com/graphql")
            .okHttpClient(get(named("githubGraphql")) { parametersOf(githubToken) })
            .build()
    }
}
