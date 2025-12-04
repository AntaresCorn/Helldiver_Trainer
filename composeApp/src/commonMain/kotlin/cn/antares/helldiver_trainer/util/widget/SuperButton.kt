package cn.antares.helldiver_trainer.util.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SuperButton(
    text: String,
    painter: Painter? = null,
    enable: Boolean = true,
    onClick: () -> Unit,
) {
    SuperBaseButton(
        text = text,
        onClick = onClick,
        enable = enable,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 5.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
fun SuperButton(
    text: String,
    icon: ImageVector? = null,
    enable: Boolean = true,
    onClick: () -> Unit,
) {
    SuperBaseButton(
        text = text,
        onClick = onClick,
        enable = enable,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(start = 5.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun SuperBaseButton(
    text: String,
    onClick: () -> Unit,
    enable: Boolean = true,
    content: @Composable (() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enable,
        colors = ButtonDefaults.buttonColors().copy(
            disabledContainerColor = Color.Gray,
            disabledContentColor = Color.Black,
        ),
    ) {
        Text(text, fontSize = 14.sp)
        content?.invoke()
    }
}