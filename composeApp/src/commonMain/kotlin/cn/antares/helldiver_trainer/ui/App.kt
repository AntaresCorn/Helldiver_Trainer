package cn.antares.helldiver_trainer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import cn.antares.helldiver_trainer.GameViewModel
import cn.antares.helldiver_trainer.NavRoute
import cn.antares.helldiver_trainer.util.AppColors
import cn.antares.helldiver_trainer.util.SharedKVManager
import cn.antares.helldiver_trainer.util.WindowInfoManager
import cn.antares.helldiver_trainer.util.WindowInfoManagerImpl
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val LocalNavController =
    staticCompositionLocalOf<NavHostController> { error("No NavController provided") }
val LocalFragmentNavController =
    staticCompositionLocalOf<NavHostController> { error("No FragmentNavController provided") }

@Composable
fun App(
    navController: NavHostController = rememberNavController(),
    fragmentNavController: NavHostController = rememberNavController(),
) {
    KoinApplication(
        application = { modules(getDiModules()) },
    ) {
        koinInject<WindowInfoManager>().Init()
        MaterialTheme(colorScheme = AppColors) {
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalFragmentNavController provides fragmentNavController,
            ) {
                NavRoute.AppNavHost()
            }
        }
    }
}

private fun getDiModules() = module {
    viewModelOf(::GameViewModel)
    singleOf(::WindowInfoManagerImpl) { bind<WindowInfoManager>() }
    singleOf(::SharedKVManager)
}