package cn.antares.helldiver_trainer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import cn.antares.helldiver_trainer.MR
import cn.antares.helldiver_trainer.NavRoute
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.delay

@Composable
fun Splash() {
    val navController = LocalNavController.current
    LaunchedEffect(Unit) {
        delay(500)
        navController.navigate(NavRoute.RouteList.Main.MainContainer) {
            popUpTo(NavRoute.RouteList.Splash) { inclusive = true }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Image(
            painter = painterResource(MR.images.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
    }
}