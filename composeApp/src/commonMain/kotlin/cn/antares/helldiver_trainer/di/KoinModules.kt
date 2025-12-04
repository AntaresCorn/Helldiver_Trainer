package cn.antares.helldiver_trainer.di

import cn.antares.helldiver_trainer.data.github.datastore.GithubCloudDataStore
import cn.antares.helldiver_trainer.data.github.datastore.createGithubCloudDataStore
import cn.antares.helldiver_trainer.data.github.repository.GithubRepository
import cn.antares.helldiver_trainer.data.github.repository.GithubRepositoryImpl
import cn.antares.helldiver_trainer.util.SharedKVManager
import cn.antares.helldiver_trainer.util.ThemeState
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.util.WindowInfoManagerImpl
import cn.antares.helldiver_trainer.viewmodel.AppViewModel
import cn.antares.helldiver_trainer.viewmodel.ArrowButtonViewModel
import cn.antares.helldiver_trainer.viewmodel.GameViewModel
import de.jensklingenberg.ktorfit.Ktorfit
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val GlobalComponentModule = module {
    singleOf(::WindowInfoManagerImpl) { bind<WindowInfoManager>() }
    singleOf(::SharedKVManager)
    singleOf(::ThemeState)
}

val ViewModelModule = module {
    viewModelOf(::GameViewModel)
    viewModelOf(::AppViewModel)
    viewModelOf(::ArrowButtonViewModel)
}

val DataModule = module {
    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
            install(Logging) {
                level = LogLevel.BODY
                format = LoggingFormat.OkHttp
                logger = object : Logger {
                    override fun log(message: String) {
                        Napier.v(message, null, "HTTP Client")
                    }
                }
            }
        }.also { Napier.base(DebugAntilog()) }
    }
    single<Ktorfit>(named("GitHubKtorfit")) {
        Ktorfit.Builder()
            .baseUrl("https://api.github.com/")
            .httpClient(get<HttpClient>())
            .build()
    }
    single<GithubCloudDataStore> { get<Ktorfit>(named("GitHubKtorfit")).createGithubCloudDataStore() }
    singleOf(::GithubRepositoryImpl) { bind<GithubRepository>() }
}