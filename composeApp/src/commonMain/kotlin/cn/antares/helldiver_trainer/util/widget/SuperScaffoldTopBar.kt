package cn.antares.helldiver_trainer.util.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.antares.helldiver_trainer.ui.LocalNavController

@Composable
fun SuperScaffoldTopBar(
    navController: NavHostController = LocalNavController.current,
    title: String = "",
    actions: @Composable () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SuperCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = { navController.navigateUp() },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = 18.sp,
            )
        }
        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
            actions()
        }
    }
}