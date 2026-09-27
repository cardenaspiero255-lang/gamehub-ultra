package com.cardenaspiero255.gamehubultra.data

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecoverableFutureInitializerTest {

    @Test
    fun failedInitializationIsRecreatedForTheNextAccess() {
        val executor = Executors.newSingleThreadExecutor()
        var attempts = 0
        try {
            val initializer = RecoverableFutureInitializer(
                executor = executor
            ) {
                attempts += 1
                if (attempts == 1) error("initialization failed")
                "ready"
            }

            assertFailsWith<ExecutionException> {
                initializer.get()
            }

            assertEquals("ready", initializer.get())
            assertEquals(2, attempts)
        } finally {
            executor.shutdownNow()
        }
    }
}
