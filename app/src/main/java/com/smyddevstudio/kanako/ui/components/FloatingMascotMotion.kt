package com.smyddevstudio.kanako.ui.components

internal enum class MascotPhase { RESTING, CREEPING, LOADING_START, LOADING_LOOP, LOADING_END, DRAGGING }

internal data class FloatingMascotMotion(
    val phase: MascotPhase = MascotPhase.CREEPING,
    val reading: Boolean = false,
    val loopPass: Int = 0,
    val readQueued: Boolean = false
) {
    fun update(reading: Boolean, visible: Boolean, motionEnabled: Boolean): FloatingMascotMotion {
        val next = when {
            !visible || !motionEnabled -> MascotPhase.RESTING
            phase == MascotPhase.RESTING && reading -> MascotPhase.LOADING_START
            else -> phase
        }
        return copy(phase = next, reading = reading,
            readQueued = readQueued && visible && motionEnabled)
    }

    fun finished(expected: MascotPhase): FloatingMascotMotion {
        if (phase != expected) return this
        return copy(
            phase = when (phase) {
                MascotPhase.CREEPING, MascotPhase.LOADING_END ->
                    if (reading || readQueued) MascotPhase.LOADING_START else MascotPhase.RESTING
                MascotPhase.LOADING_START -> MascotPhase.LOADING_LOOP
                MascotPhase.LOADING_LOOP -> if (reading) MascotPhase.LOADING_LOOP else MascotPhase.LOADING_END
                else -> phase
            },
            loopPass = if (phase == MascotPhase.LOADING_LOOP && reading) loopPass + 1 else loopPass,
            readQueued = readQueued && phase != MascotPhase.CREEPING && phase != MascotPhase.LOADING_END
        )
    }

    fun requestReadingAnimation() = when (phase) {
        MascotPhase.RESTING -> copy(phase = MascotPhase.LOADING_START)
        MascotPhase.CREEPING, MascotPhase.LOADING_END, MascotPhase.DRAGGING -> copy(readQueued = true)
        else -> this
    }

    fun startDrag() = copy(phase = MascotPhase.DRAGGING)
    fun endDrag() = copy(phase = if (readQueued) MascotPhase.LOADING_START else MascotPhase.RESTING,
        readQueued = false)
}
