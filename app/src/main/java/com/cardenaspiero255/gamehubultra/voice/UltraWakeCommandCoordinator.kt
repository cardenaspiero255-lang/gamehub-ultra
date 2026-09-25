package com.cardenaspiero255.gamehubultra.voice

/**
 * Serializes the continuous-listening state so SpeechRecognizer and command
 * execution cannot overlap or be started twice during Activity/game switches.
 */
internal class UltraWakeCommandCoordinator {
    private val lock = Any()
    private var recognitionActive = false
    private var commandRunning = false

    fun tryStartRecognition(): Boolean = synchronized(lock) {
        if (recognitionActive || commandRunning) {
            false
        } else {
            recognitionActive = true
            true
        }
    }

    fun onRecognitionFinished() {
        synchronized(lock) {
            recognitionActive = false
        }
    }

    fun tryBeginCommand(): Boolean = synchronized(lock) {
        recognitionActive = false
        if (commandRunning) {
            false
        } else {
            commandRunning = true
            true
        }
    }

    fun finishCommand(): Boolean = synchronized(lock) {
        if (!commandRunning) {
            false
        } else {
            commandRunning = false
            true
        }
    }

    fun isCommandRunning(): Boolean = synchronized(lock) {
        commandRunning
    }
}
