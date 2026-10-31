package ma.remorquepro.cac.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Clair = lightColorScheme(
    primary = Color(0xFF1F3864),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4F5),
    onPrimaryContainer = Color(0xFF0E1D3A),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0A8),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF2E6B4F),
    tertiaryContainer = Color(0xFFCBEBD9),
    onTertiaryContainer = Color(0xFF0B2E1F),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFF7F8FC),
    surfaceVariant = Color(0xFFE3E7EF),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

private val Sombre = darkColorScheme(
    primary = Color(0xFFAFC6F2),
    onPrimary = Color(0xFF0E1D3A),
    primaryContainer = Color(0xFF2C4675),
    onPrimaryContainer = Color(0xFFDCE4F5),
    secondary = Color(0xFFF2C062),
    secondaryContainer = Color(0xFF5F3F00),
    onSecondaryContainer = Color(0xFFFFE0A8),
    tertiary = Color(0xFF95D4B4),
    tertiaryContainer = Color(0xFF14513A),
    onTertiaryContainer = Color(0xFFCBEBD9),
    background = Color(0xFF121418),
    surface = Color(0xFF121418),
)

@Composable
fun ThemeRemorque(sombre: Boolean = isSystemInDarkTheme(), contenu: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (sombre) Sombre else Clair, content = contenu)
}
