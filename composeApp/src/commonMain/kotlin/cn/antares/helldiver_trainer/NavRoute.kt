package cn.antares.helldiver_trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cn.antares.helldiver_trainer.ui.LocalFragmentNavController
import cn.antares.helldiver_trainer.ui.LocalNavController
import cn.antares.helldiver_trainer.ui.MainContainer
import cn.antares.helldiver_trainer.ui.MainFragment
import cn.antares.helldiver_trainer.ui.SettingsFragment
import cn.antares.helldiver_trainer.ui.Splash
import cn.antares.helldiver_trainer.ui.StratagemSelectorPage
import cn.antares.helldiver_trainer.ui.Trainer
import kotlinx.serialization.Serializable

object NavRoute {
    sealed class RouteList {
        @Serializable
        object Splash : RouteList()

        object Main {
            @Serializable
            object MainContainer : RouteList()

            @Serializable
            object MainFragment : RouteList()

            @Serializable
            object SettingsFragment : RouteList()
        }

        @Serializable
        object Trainer : RouteList()

        @Serializable
        object StratagemSelector : RouteList()
    }

    @Composable
    fun AppNavHost(modifier: Modifier = Modifier) {
        val navController = LocalNavController.current
        NavHost(
            navController = navController,
            startDestination = RouteList.Splash,
            modifier = modifier,
        ) {
            composable<RouteList.Splash> {
                Splash()
            }
            composable<RouteList.Main.MainContainer> {
                MainContainer()
            }
            composable<RouteList.Trainer> {
                Trainer()
            }
            composable<RouteList.StratagemSelector> {
                StratagemSelectorPage()
            }
        }
    }

    @Composable
    fun MainNavHost(modifier: Modifier = Modifier) {
        val fragmentNavController = LocalFragmentNavController.current
        NavHost(
            navController = fragmentNavController,
            startDestination = RouteList.Main.MainFragment,
            modifier = modifier,
        ) {
            composable<RouteList.Main.MainFragment> {
                MainFragment()
            }
            composable<RouteList.Main.SettingsFragment> {
                SettingsFragment()
            }
        }
    }
}