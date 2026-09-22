package com.cardenaspiero255.gamehubultra.voice

object VoicePermissionGate {
    fun canStartRecognition(
        microphoneGranted: Boolean,
        recognitionAvailable: Boolean
    ): Boolean = microphoneGranted && recognitionAvailable
}
