package cn.antares.helldiver_trainer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.NavRoute
import cn.antares.helldiver_trainer.bridge.openWebPage

@Composable
fun MainFragment() {
    val navController = LocalNavController.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = {
                    navController.navigate(NavRoute.RouteList.Trainer) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(NavRoute.RouteList.Main.MainContainer) {
                            saveState = true
                        }
                    }
                },
            ) {
                Text("战备英雄", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.size(30.dp))
            Button(
                onClick = { openWebPage("https://helldiverscompanion.com/") },
            ) {
                Text("银河战争地图", fontSize = 20.sp)
            }
        }
    }
}