package com.cardenaspiero255.gamehubultra

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns typed Ultra queries at the screen level so removing the assistant card
 * from composition does not cancel a submitted request.
 */
internal class UltraAssistantQueryRunner(
    private val ownerScope: CoroutineScope
) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        if (_isRunning.value) {
            return ownerScope.launch { }
        }

        _isRunning.value = true
        return ownerScope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                _isRunning.value = false
            }
        }
    }
}
