package app.cursor.android.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class PointerHaptic {
    Click,
    Tab,
    Toggle,
    Open,
    Close,
    Success,
    Warning,
    Error,
    Tick,
}

fun View.pointerHaptic(kind: PointerHaptic, enabled: Boolean) {
    if (!enabled) return
    val constant = kind.toAndroidConstant()
    performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
}

private fun PointerHaptic.toAndroidConstant(): Int {
    val sdk = Build.VERSION.SDK_INT
    return when (this) {
        PointerHaptic.Click -> HapticFeedbackConstants.CONTEXT_CLICK
        PointerHaptic.Tab -> HapticFeedbackConstants.CLOCK_TICK
        PointerHaptic.Toggle -> HapticFeedbackConstants.CLOCK_TICK
        PointerHaptic.Open -> if (sdk >= 30) {
            HapticFeedbackConstants.GESTURE_START
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        PointerHaptic.Close -> if (sdk >= 30) {
            HapticFeedbackConstants.GESTURE_END
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        PointerHaptic.Success -> if (sdk >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        PointerHaptic.Warning -> if (sdk >= 30) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        PointerHaptic.Error -> if (sdk >= 30) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        PointerHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
    }
}

@Composable
fun rememberPointerHaptics(): (PointerHaptic) -> Unit {
    val view = LocalView.current
    val enabled = LocalPointerUi.current.haptics
    return remember(view, enabled) {
        { kind -> view.pointerHaptic(kind, enabled) }
    }
}
