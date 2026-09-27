package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.ai.UltraMemoryPersistence
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRepository
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UltraMemoryRepositoryProviderTest {

    @Test
    fun failedInitializationIsInvalidatedSoNextGetCanRecover() {
        val executor = Executors.newSingleThreadExecutor()
        var attempts = 0
        val persistence = object : UltraMemoryPersistence {
            override fun read(): String? = null
            override fun write(serialized: String) = Unit
            override fun clear() = Unit
        }
        val provider = UltraMemoryRepositoryProvider(
            executor = executor,
            factory = {
                attempts += 1
                if (attempts == 1) error("init failed")
                UltraMemoryRepository(persistence)
            }
        )

        try {
            assertFailsWith<IllegalStateException> {
                provider.get()
            }

            provider.get()

            assertEquals(2, attempts)
        } finally {
            executor.shutdownNow()
        }
    }


    @Test
    fun getCanInitializeWhenCalledFromTheConfiguredSingleThreadExecutor() {
        val executor = Executors.newSingleThreadExecutor()
        val persistence = object : UltraMemoryPersistence {
            override fun read(): String? = null
            override fun write(serialized: String) = Unit
            override fun clear() = Unit
        }
        val provider = UltraMemoryRepositoryProvider(
            executor = executor,
            factory = { UltraMemoryRepository(persistence) }
        )

        try {
            val repository = executor
                .submit<UltraMemoryRepository> { provider.get() }
                .get(1, TimeUnit.SECONDS)

            assertEquals(repository, provider.get())
        } finally {
            executor.shutdownNow()
        }
    }


    @Test
    fun successfulInitializationIsReused() {
        val executor = Executors.newSingleThreadExecutor()
        var attempts = 0
        val persistence = object : UltraMemoryPersistence {
            override fun read(): String? = null
            override fun write(serialized: String) = Unit
            override fun clear() = Unit
        }
        val provider = UltraMemoryRepositoryProvider(
            executor = executor,
            factory = {
                attempts += 1
                UltraMemoryRepository(persistence)
            }
        )

        try {
            val first = provider.get()
            val second = provider.get()

            assertEquals(first, second)
            assertEquals(1, attempts)
        } finally {
            executor.shutdownNow()
        }
    }
}
