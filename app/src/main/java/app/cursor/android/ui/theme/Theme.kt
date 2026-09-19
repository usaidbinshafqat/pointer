package app.cursor.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.cursor.android.R
import app.cursor.android.ui.LocalPointerUi
import app.cursor.android.ui.PointerUiPrefs
import app.cursor.android.ui.fontWeightValue
import app.cursor.android.ui.textScaleFactor

internal val PointerSans = FontFamily(
    Font(R.font.fira_sans_thin, FontWeight.Thin),
    Font(R.font.fira_sans_thin_italic, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.fira_sans_extralight, FontWeight.ExtraLight),
    Font(R.font.fira_sans_extralight_italic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.fira_sans_light, FontWeight.Light),
    Font(R.font.fira_sans_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.fira_sans_regular, FontWeight.Normal),
    Font(R.font.fira_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.fira_sans_medium, FontWeight.Medium),
    Font(R.font.fira_sans_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.fira_sans_semibold, FontWeight.SemiBold),
    Font(R.font.fira_sans_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.fira_sans_bold, FontWeight.Bold),
    Font(R.font.fira_sans_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.fira_sans_extrabold, FontWeight.ExtraBold),
    Font(R.font.fira_sans_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.fira_sans_black, FontWeight.Black),
    Font(R.font.fira_sans_black_italic, FontWeight.Black, FontStyle.Italic),
)

internal val PointerMono = FontFamily(
    Font(R.font.fira_code, FontWeight.Light),
    Font(R.font.fira_code, FontWeight.Normal),
    Font(R.font.fira_code, FontWeight.Medium),
    Font(R.font.fira_code, FontWeight.Bold),
)

private val DefaultTypography = Typography()

private fun TextStyle.pointerSans(weight: FontWeight) =
    copy(fontFamily = PointerSans, fontWeight = weight)

private fun titleWeightFor(id: String): FontWeight = when (id) {
    "thin", "extralight" -> FontWeight.Normal
    "light", "regular" -> FontWeight.Medium
    "medium" -> FontWeight.SemiBold
    "semibold" -> FontWeight.Bold
    "bold" -> FontWeight.ExtraBold
    else -> FontWeight.Black
}

private fun pointerTypography(fontWeightId: String): Typography {
    val body = fontWeightValue(fontWeightId)
    val title = titleWeightFor(fontWeightId)
    return Typography(
        displayLarge = DefaultTypography.displayLarge.pointerSans(body),
        displayMedium = DefaultTypography.displayMedium.pointerSans(body),
        displaySmall = DefaultTypography.displaySmall.pointerSans(body),
        headlineLarge = DefaultTypography.headlineLarge.pointerSans(title),
        headlineMedium = DefaultTypography.headlineMedium.pointerSans(title),
        headlineSmall = DefaultTypography.headlineSmall.pointerSans(title),
        titleLarge = DefaultTypography.titleLarge.pointerSans(title),
        titleMedium = DefaultTypography.titleMedium.pointerSans(title),
        titleSmall = DefaultTypography.titleSmall.pointerSans(title),
        bodyLarge = DefaultTypography.bodyLarge.pointerSans(body),
        bodyMedium = DefaultTypography.bodyMedium.pointerSans(body),
        bodySmall = DefaultTypography.bodySmall.pointerSans(body),
        labelLarge = DefaultTypography.labelLarge.pointerSans(title),
        labelMedium = DefaultTypography.labelMedium.pointerSans(title),
        labelSmall = DefaultTypography.labelSmall.pointerSans(title),
    )
}

private fun Typography.scaled(factor: Float): Typography {
    if (factor == 1f) return this
    fun TextStyle.scale() = copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)
    return copy(
        displayLarge = displayLarge.scale(),
        displayMedium = displayMedium.scale(),
        displaySmall = displaySmall.scale(),
        headlineLarge = headlineLarge.scale(),
        headlineMedium = headlineMedium.scale(),
        headlineSmall = headlineSmall.scale(),
        titleLarge = titleLarge.scale(),
        titleMedium = titleMedium.scale(),
        titleSmall = titleSmall.scale(),
        bodyLarge = bodyLarge.scale(),
        bodyMedium = bodyMedium.scale(),
        bodySmall = bodySmall.scale(),
        labelLarge = labelLarge.scale(),
        labelMedium = labelMedium.scale(),
        labelSmall = labelSmall.scale(),
    )
}

private val RoundedMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Brand accent when Material You is off. Dark theme uses a lighter tint of the same blue. */
private val PointerAccent = Color(0xFF0394FC)
private val PointerAccentDark = Color(0xFF8ACDFF)

private val PointerLightColorScheme = lightColorScheme(
    primary = PointerAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1EAFF),
    onPrimaryContainer = Color(0xFF001D36),
    inversePrimary = PointerAccentDark,
    secondary = Color(0xFF4A626F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE7F6),
    onSecondaryContainer = Color(0xFF051E29),
    tertiary = Color(0xFF5A5B7D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E0FF),
    onTertiaryContainer = Color(0xFF171837),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDCE3EB),
    onSurfaceVariant = Color(0xFF40484F),
    surfaceTint = PointerAccent,
    outline = Color(0xFF707880),
    outlineVariant = Color(0xFFC0C7CF),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEEF1F5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainer = Color(0xFFEBEEF2),
    surfaceContainerHigh = Color(0xFFE5E8EC),
    surfaceContainerHighest = Color(0xFFDFE3E7),
)

private val PointerDarkColorScheme = darkColorScheme(
    primary = PointerAccentDark,
    onPrimary = Color(0xFF00344F),
    primaryContainer = PointerAccent,
    onPrimaryContainer = Color.White,
    inversePrimary = PointerAccent,
    secondary = Color(0xFFB1CBD9),
    onSecondary = Color(0xFF1C333F),
    secondaryContainer = Color(0xFF344A56),
    onSecondaryContainer = Color(0xFFCDE7F6),
    tertiary = Color(0xFFC3C3EA),
    onTertiary = Color(0xFF2C2D4D),
    tertiaryContainer = Color(0xFF434465),
    onTertiaryContainer = Color(0xFFE0E0FF),
    background = Color(0xFF101417),
    onBackground = Color(0xFFDFE3E7),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFDFE3E7),
    surfaceVariant = Color(0xFF40484F),
    onSurfaceVariant = Color(0xFFC0C7CF),
    surfaceTint = PointerAccentDark,
    outline = Color(0xFF8A9299),
    outlineVariant = Color(0xFF40484F),
    inverseSurface = Color(0xFFDFE3E7),
    inverseOnSurface = Color(0xFF2D3135),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262A2E),
    surfaceContainerHighest = Color(0xFF31353A),
)

@Composable
fun CursorTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = true,
    spinnerStyle: String = "wavy",
    textScaleId: String = "m",
    fontWeightId: String = "regular",
    haptics: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> PointerDarkColorScheme
        else -> PointerLightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val factor = textScaleFactor(textScaleId)
    CompositionLocalProvider(
        LocalPointerUi provides PointerUiPrefs(
            spinnerStyle = spinnerStyle,
            textScale = factor,
            haptics = haptics,
        ),
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = pointerTypography(fontWeightId).scaled(factor),
            shapes = RoundedMaterialShapes,
            content = content,
        )
    }
}
