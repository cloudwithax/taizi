package com.taizi.ui.components

import androidx.compose.ui.MotionDurationScale

/**
 * Forces animations to run at full duration inside the coroutine it's launched
 * with, ignoring the system's animator duration scale.
 *
 * Handhelds running GSI builds routinely ship with Developer Options' animator
 * scale at 0, which makes Compose fast-forward every `animate*` call straight to
 * its end value. That's fine for decoration, but it silently breaks motion we
 * depend on: carousel paging becomes a teleport, and a hold-to-confirm timer
 * would elapse instantly. Launch those with this element in context.
 */
val FullMotionScale = object : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}
