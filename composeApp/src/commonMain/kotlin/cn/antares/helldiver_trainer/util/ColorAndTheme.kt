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
    val IlluminateColor = Color(0xFF6A3ABA)
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
        val helldiverTheme = lightColorScheme(
            primary = HelldiverColor,
            onPrimary = Color.Black,
        )
        val automatonTheme = lightColorScheme(
            primary = AutomatonColor,
            onPrimary = Color.Black,
        )
        val illuminateTheme = lightColorScheme(
            primary = IlluminateColor,
            onPrimary = Color.White,
        )
        val terminidTheme = lightColorScheme(
            primary = TerminidColor,
            onPrimary = Color.Black,
        )

        fun AppTheme.getPrimaryColor(): Color {
            return when (this) {
                AppTheme.HELLDIVER -> HelldiverColor
                AppTheme.AUTOMATON -> AutomatonColor
                AppTheme.ILLUMINATE -> IlluminateColor
                AppTheme.TERMINID -> TerminidColor
            }
        }

        fun AppTheme.getColorScheme() = when (this) {
            AppTheme.HELLDIVER -> helldiverTheme
            AppTheme.AUTOMATON -> automatonTheme
            AppTheme.ILLUMINATE -> illuminateTheme
            AppTheme.TERMINID -> terminidTheme
        }
    }

    var currentTheme: AppTheme by mutableStateOf(AppTheme.HELLDIVER)
}