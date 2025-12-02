package cn.antares.helldiver_trainer.util

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import cn.antares.helldiver_trainer.util.HellColors.AutomatonColor
import cn.antares.helldiver_trainer.util.HellColors.HelldiverColor
import cn.antares.helldiver_trainer.util.HellColors.IlluminateColor
import cn.antares.helldiver_trainer.util.HellColors.TerminidColor

object HellColors {
    val HelldiverColor = Color(0xFFFDE701)
    val AutomatonColor = Color(0xFFFC6C72)
    val IlluminateColor = Color(0xFFDA59F9)
    val TerminidColor = Color(0xFFFDB801)
    val PrimaryColor = HelldiverColor
}

class ThemeState {
    sealed interface AppTheme {
        object HELLDIVER : AppTheme
        object AUTOMATON : AppTheme
        object ILLUMINATE : AppTheme
        object TERMINID : AppTheme
    }

    object MyTheme {
        private val baseTheme = lightColorScheme(
            onPrimary = Color.Black,
            background = Color.DarkGray,
            surface = Color.DarkGray,
            surfaceContainer = Color.DarkGray,
            surfaceContainerLow = Color.DarkGray,
            surfaceContainerLowest = Color.DarkGray,
            surfaceContainerHigh = Color.DarkGray,
            surfaceContainerHighest = Color.DarkGray,
            onBackground = Color.White,
        )
        val helldiverTheme = baseTheme.copy(
            primary = HelldiverColor,
        )
        val automatonTheme = baseTheme.copy(
            primary = AutomatonColor,
        )
        val illuminateTheme = baseTheme.copy(
            primary = IlluminateColor,
        )
        val terminidTheme = baseTheme.copy(
            primary = TerminidColor,
        )

        fun AppTheme.getColorScheme() = when (this) {
            AppTheme.HELLDIVER -> helldiverTheme
            AppTheme.AUTOMATON -> automatonTheme
            AppTheme.ILLUMINATE -> illuminateTheme
            AppTheme.TERMINID -> terminidTheme
        }
    }

    var currentTheme: AppTheme by mutableStateOf(AppTheme.HELLDIVER)
}