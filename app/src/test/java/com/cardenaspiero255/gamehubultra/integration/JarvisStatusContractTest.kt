package com.cardenaspiero255.gamehubultra.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisStatusContractTest {
    @Test fun onlyJarvisWithSignaturePermissionMayReadStatus() {
        assertTrue(JarvisStatusContract.authorize(
            setOf("com.cardenaspiero255.jarvis"), signaturePermissionGranted = true,
            path = "v1/status"))
        assertFalse(JarvisStatusContract.authorize(
            setOf("com.cardenaspiero255.jarvis"), signaturePermissionGranted = false,
            path = "v1/status"))
        assertFalse(JarvisStatusContract.authorize(
            setOf("com.attacker.jarvis"), signaturePermissionGranted = true,
            path = "v1/status"))
        assertFalse(JarvisStatusContract.authorize(
            emptySet(), signaturePermissionGranted = true, path = "v1/status"))
        assertFalse(JarvisStatusContract.authorize(
            setOf("com.cardenaspiero255.jarvis"), signaturePermissionGranted = true,
            path = "v1/run"))
    }

    @Test fun responseIsInertVersionedAndNeverPromisesExecution() {
        val reply = JarvisStatusContract.reply()
        assertEquals(1, reply.version)
        assertEquals("com.cardenaspiero255.gamehubultra", reply.packageName)
        assertEquals("READ_ONLY", reply.status)
        assertFalse(reply.remoteExecutionEnabled)
        assertFalse(reply.toolExecutionEnabled)
    }

    @Test fun contractPinsOneExactAuthorityAndPermission() {
        assertEquals("com.cardenaspiero255.gamehubultra.jarvis.status",
            JarvisStatusContract.AUTHORITY)
        assertEquals("com.cardenaspiero255.gamehubultra.permission.JARVIS_READ_STATUS",
            JarvisStatusContract.PERMISSION)
    }
}
