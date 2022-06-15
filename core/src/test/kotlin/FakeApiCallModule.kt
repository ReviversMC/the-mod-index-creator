import com.github.reviversmc.themodindex.creator.core.apicalls.CurseForgeApiCall
import com.github.reviversmc.themodindex.creator.core.apicalls.ModrinthApiCall
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit

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
}