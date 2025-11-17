package cn.antares.helldiver_trainer.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SuperDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    title: String? = null,
    message: String? = null,
    confirmButtonText: String? = null,
    confirmButtonCallback: (() -> Unit)? = null,
    dismissButtonText: String? = null,
    dismissButtonCallback: (() -> Unit)? = null,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = if (title.isNullOrBlank().not()) {
            { Text(title, color = Color.White) }
        } else null,
        text = if (message.isNullOrBlank().not()) {
            { Text(message, color = Color.White) }
        } else null,
        confirmButton = if (confirmButtonText.isNullOrBlank().not()) {
            {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { confirmButtonCallback?.invoke() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(confirmButtonText)
                }
            }
        } else {
            {}
        },
        dismissButton = if (dismissButtonText.isNullOrBlank().not()) {
            {
                Box(
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { dismissButtonCallback?.invoke() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(dismissButtonText)
                }
            }
        } else null,
    )
}