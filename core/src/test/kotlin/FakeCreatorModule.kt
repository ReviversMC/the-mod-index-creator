import com.github.reviversmc.themodindex.creator.core.Creator
import com.github.reviversmc.themodindex.creator.core.ModIndexCreator
import com.github.reviversmc.themodindex.creator.core.dependency.dependencyModule
import com.github.reviversmc.themodindex.creator.core.modreader.modReaderModule
import org.koin.core.parameter.parametersOf
import org.koin.dsl.bind
import org.koin.dsl.module

val fakeCreatorModule = module {
    factory { (curseForgeApiKey: String,  curseForgeBaseUrl: String, gitHubApiKey: String, modrinthBaseUrl: String) ->
        ModIndexCreator(
            get(),
            curseForgeApiKey,
            get { parametersOf(curseForgeBaseUrl) },
            { get { parametersOf(gitHubApiKey) } },
            get { parametersOf(modrinthBaseUrl) },
            get()
        )
    } bind Creator::class
    includes(fakeApiCallModule, dependencyModule, modReaderModule)
}