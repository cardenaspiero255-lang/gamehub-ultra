package com.cardenaspiero255.gamehubultra.ai

/**
 * Package-local assertion used by resilience tests in files where connector
 * safety prevents rewriting credential-adjacent fixtures.
 */
internal fun assertFalse(actual: Boolean) {
    kotlin.test.assertFalse(actual)
}
