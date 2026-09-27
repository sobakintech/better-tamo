package lt.bettertamo.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF285E49), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9ECD5), onPrimaryContainer = Color(0xFF163C2B),
    secondary = Color(0xFF52654F), secondaryContainer = Color(0xFFE3EAD9),
    onSecondaryContainer = Color(0xFF303F2A),
    tertiary = Color(0xFF805C24), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9E7BD), onTertiaryContainer = Color(0xFF2A1800),
    background = Color(0xFFF6F8F4), onBackground = Color(0xFF1C2821),
    surface = Color(0xFFF6F8F4), onSurface = Color(0xFF1C2821),
    surfaceContainer = Color(0xFFECF0E9), surfaceContainerLow = Color(0xFFF0F3EC),
    surfaceContainerLowest = Color.White, surfaceContainerHigh = Color(0xFFE5EAE1),
    surfaceVariant = Color(0xFFE4E9DF), onSurfaceVariant = Color(0xFF586254),
    outline = Color(0xFF778173), outlineVariant = Color(0xFFD9E0D4),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFADD6B7), onPrimary = Color(0xFF103824),
    primaryContainer = Color(0xFF284B35), onPrimaryContainer = Color(0xFFD9ECD5),
    secondary = Color(0xFFC0CEB5), secondaryContainer = Color(0xFF394732),
    onSecondaryContainer = Color(0xFFDFE9D4),
    tertiary = Color(0xFFE9C16C), onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5D4200), onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF141B17), onBackground = Color(0xFFE2E9DF),
    surface = Color(0xFF141B17), onSurface = Color(0xFFE2E9DF),
    surfaceContainer = Color(0xFF202A23), surfaceContainerLow = Color(0xFF1A241E),
    surfaceContainerLowest = Color(0xFF1D2720), surfaceContainerHigh = Color(0xFF2B352D),
    surfaceVariant = Color(0xFF354033), onSurfaceVariant = Color(0xFFBDC8B6),
    outline = Color(0xFF8C9787), outlineVariant = Color(0xFF414D3C),
)
private val type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun BetterTamoTheme(theme: String = "system", content: @Composable () -> Unit) {
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity, dark) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = type, content = content)
}
