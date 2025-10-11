package cn.antares.helldiver_trainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import cn.antares.helldiver_trainer.MR
import cn.antares.helldiver_trainer.NavRoute
import cn.antares.helldiver_trainer.util.ThemeState
import cn.antares.helldiver_trainer.util.ThemeState.MyTheme.getPrimaryColor
import cn.antares.helldiver_trainer.util.WindowInfoManager
import dev.icerock.moko.resources.compose.painterResource
import org.koin.compose.koinInject

private data class MainFragmentDestination(
    val name: String,
    val route: NavRoute.RouteList,
    val icon: ImageVector,
)

private val navList = listOf(
    MainFragmentDestination(
        "主页",
        NavRoute.RouteList.Main.MainFragment,
        Icons.Rounded.Home,
    ),
    MainFragmentDestination(
        "设置",
        NavRoute.RouteList.Main.SettingsFragment,
        Icons.Rounded.Settings,
    ),
)

@Composable
fun MainContainer(
    windowInfoManager: WindowInfoManager = koinInject(),
    themeState: ThemeState = koinInject(),
) {
    val windowInfo by windowInfoManager.windowInfoFlow.collectAsState()
    val fragmentNavController = LocalFragmentNavController.current
    val navBackStackEntry by fragmentNavController.currentBackStackEntryAsState()
    val destinationItems by remember { mutableStateOf(navList) }

    fun bottomNav(destination: MainFragmentDestination) {
        fragmentNavController.navigate(destination.route) {
            popUpTo(0) {
                saveState = true
                inclusive = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navSelected(destination: MainFragmentDestination): Boolean =
        navBackStackEntry?.destination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

    Scaffold(
        bottomBar = {
            if (windowInfo.isWidthExpanded().not() && windowInfo.isHeightExpanded().not()) {
                NavigationBar(containerColor = Color.DarkGray) {
                    destinationItems.forEach { destination ->
                        NavigationBarItem(
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.name) },
                            selected = navSelected(destination),
                            onClick = { bottomNav(destination) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                unselectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedTextColor = Color.White,
                                indicatorColor = themeState.currentTheme.getPrimaryColor(),
                            ),
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        Row {
            if (windowInfo.isWidthExpanded() || windowInfo.isHeightExpanded()) {
                NavigationRail(
                    containerColor = Color.DarkGray,
                    header = {
                        Box(modifier = Modifier.padding(top = 10.dp)) {
                            Icon(
                                painterResource(MR.images.ic_launcher),
                                contentDescription = null,
                                modifier = Modifier.size(45.dp).clip(RoundedCornerShape(15))
                                    .background(themeState.currentTheme.getPrimaryColor())
                                    .padding(5.dp),
                            )
                        }
                    },
                ) {
                    destinationItems.forEach { destination ->
                        NavigationRailItem(
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.name) },
                            selected = navSelected(destination),
                            onClick = { bottomNav(destination) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                unselectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedTextColor = Color.White,
                                indicatorColor = themeState.currentTheme.getPrimaryColor(),
                            ),
                        )
                    }
                }
            }
            NavRoute.MainNavHost(Modifier.padding(paddingValues).background(Color.DarkGray))
        }
    }
}