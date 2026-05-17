package com.taizi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.taizi.ui.theme.BrandAccent

/**
 * Draws an accent border and gently scales the element while it has focus.
 *
 * Listens to Compose focus state via onFocusChanged. Make sure the element is
 * focusable (clickable / focusable / IconButton already are).
 */
fun Modifier.focusHighlight(
    shape: Shape = RoundedCornerShape(12.dp),
    accent: Color = BrandAccent,
    scale: Float = 1.06f,
    borderWidth: Dp = 3.dp
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "focusHighlightScale"
    )
    this
        .onFocusChanged { focused = it.isFocused }
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
        .then(if (focused) Modifier.border(borderWidth, accent, shape) else Modifier)
}
