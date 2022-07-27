import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.ModIndexCreator
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val fakeCreatorModule = module {
    factory { (curseForgeApiKey: String,  curseForgeBaseUrl: String, modrinthBaseUrl: String) ->
        ModIndexCreator(
            get(),
            curseForgeApiKey,
            get { parametersOf(curseForgeBaseUrl) },
            { get(named("default")) },
            get { parametersOf(modrinthBaseUrl) },
            get()
        )
    } bind Creator::class
    includes(fakeApiCallModule, dependencyModule) //TODO Use a fake dependency module
}