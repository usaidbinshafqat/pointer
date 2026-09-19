package app.cursor.android.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class PointerUiPrefs(
    val spinnerStyle: String = "wavy",
    val textScale: Float = 1f,
    val haptics: Boolean = true,
)

val LocalPointerUi = staticCompositionLocalOf { PointerUiPrefs() }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSpinner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.Transparent,
    compact: Boolean = false,
) {
    val style = LocalPointerUi.current.spinnerStyle
    val stroke = if (compact) 2.dp else 3.dp
    when (style) {
        "circular" -> CircularProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeWidth = stroke,
            strokeCap = StrokeCap.Round,
        )
        "linear" -> LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = if (trackColor == Color.Transparent) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                trackColor
            },
        )
        "linearWavy" -> LinearWavyProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = if (trackColor == Color.Transparent) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                trackColor
            },
        )
        else -> CircularWavyProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    }
}

fun spinnerStyleLabel(id: String): String = when (id) {
    "circular" -> "circular"
    "linear" -> "linear"
    "linearWavy" -> "linear wavy"
    else -> "wavy"
}

val SpinnerStyles = listOf("wavy", "circular", "linearWavy", "linear")

fun textScaleLabel(id: String): String = when (id) {
    "s" -> "small"
    "l" -> "large"
    "xl" -> "xl"
    else -> "default"
}

fun textScaleFactor(id: String): Float = when (id) {
    "s" -> 0.90f
    "l" -> 1.15f
    "xl" -> 1.30f
    else -> 1.00f
}

val TextScaleIds = listOf("s", "m", "l", "xl")

fun fontWeightLabel(id: String): String = when (id) {
    "thin" -> "thin"
    "extralight" -> "extra light"
    "regular" -> "regular"
    "medium" -> "medium"
    "semibold" -> "semi bold"
    "bold" -> "bold"
    "extrabold" -> "extra bold"
    "black" -> "black"
    else -> "regular"
}

fun fontWeightValue(id: String): FontWeight = when (id) {
    "thin" -> FontWeight.Thin
    "extralight" -> FontWeight.ExtraLight
    "light" -> FontWeight.Light
    "medium" -> FontWeight.Medium
    "semibold" -> FontWeight.SemiBold
    "bold" -> FontWeight.Bold
    "extrabold" -> FontWeight.ExtraBold
    "black" -> FontWeight.Black
    else -> FontWeight.Normal
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpinnerPreview(style: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val color = MaterialTheme.colorScheme.primary
    val linear = modifier
        .width(72.dp)
        .height(6.dp)
    when (style) {
        "circular" -> CircularProgressIndicator(
            modifier = modifier.size(size),
            color = color,
            strokeWidth = 3.dp,
            trackColor = Color.Transparent,
        )
        "linear" -> LinearProgressIndicator(modifier = linear, color = color)
        "linearWavy" -> LinearWavyProgressIndicator(modifier = linear, color = color)
        else -> CircularWavyProgressIndicator(
            modifier = modifier.size(size),
            color = color,
            trackColor = Color.Transparent,
        )
    }
}
