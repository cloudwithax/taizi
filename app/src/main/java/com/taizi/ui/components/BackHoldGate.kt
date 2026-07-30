package com.taizi.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges the hardware Back key to the Now Playing screen.
 *
 * While armed, MainActivity swallows every Back key event and forwards the
 * down/up edges here instead, so a tap can never dismiss the screen — only a
 * sustained hold does. Compose observes [pressed] to drive the hold progress.
 */
object BackHoldGate {

    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _pressed = MutableStateFlow(false)
    val pressed: StateFlow<Boolean> = _pressed.asStateFlow()

    val isArmed: Boolean get() = _armed.value

    fun arm() {
        _pressed.value = false
        _armed.value = true
    }

    fun disarm() {
        _armed.value = false
        _pressed.value = false
    }

    /**
     * True between a completed hold and the physical release that follows it.
     * A held Back key auto-repeats every ~50ms, so without this the repeats
     * left over after the guard closes would keep backing out of the library.
     */
    @Volatile
    var isDraining: Boolean = false
        private set

    fun drainUntilRelease() { isDraining = true }

    fun endDrain() { isDraining = false }

    fun press() {
        if (_armed.value) _pressed.value = true
    }

    fun release() {
        _pressed.value = false
    }
}
