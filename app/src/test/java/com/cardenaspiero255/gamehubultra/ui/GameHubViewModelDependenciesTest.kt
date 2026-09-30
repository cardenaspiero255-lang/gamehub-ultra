package com.cardenaspiero255.gamehubultra.ui

import com.cardenaspiero255.gamehubultra.data.GameLibraryStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSelectionStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionLifecycleCoordinatorFactory
import com.cardenaspiero255.gamehubultra.data.PerformanceHistoryStateRepository
import java.lang.reflect.Type
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameHubViewModelDependenciesTest {
    @Test
    fun dependencyFactoryContractIsAndroidFreeAndExplicit() {
        val factory = GameHubViewModelDependencyFactory::class.java
        val dependencyContract = GameHubViewModelDependencies::class.java
        val sessionCoordinatorFactory = GameSessionLifecycleCoordinatorFactory::class.java
        val exposedTypes =
            listOf(factory, dependencyContract, sessionCoordinatorFactory).flatMap { contract ->
                contract.methods.flatMap { method ->
                    method.genericParameterTypes.toList() + method.genericReturnType
                } + contract.genericInterfaces.toList()
            } + dependencyContract.constructors.flatMap { constructor ->
                constructor.genericParameterTypes.toList()
            }

        assertFalse(
            exposedTypes.any(::containsAndroidPlatformType),
            "ViewModel dependency boundaries must not expose Android or AndroidX types"
        )
        assertTrue(factory.methods.any { it.name == "create" })
        assertTrue(
            dependencyContract.methods.any {
                it.name == "getSelectionRepository" &&
                    it.returnType == GameSelectionStateRepository::class.java
            }
        )
        assertTrue(
            dependencyContract.methods.any {
                it.name == "getLibraryRepository" &&
                    it.returnType == GameLibraryStateRepository::class.java
            }
        )
        assertTrue(
            dependencyContract.methods.any {
                it.name == "getPerformanceHistoryRepository" &&
                    it.returnType == PerformanceHistoryStateRepository::class.java
            }
        )
        assertTrue(
            dependencyContract.methods.any {
                it.name == "getSessionRepository" &&
                    it.returnType == GameSessionStateRepository::class.java
            }
        )
        assertTrue(
            dependencyContract.methods.any {
                it.name == "getSessionCoordinatorFactory" &&
                    it.returnType == GameSessionLifecycleCoordinatorFactory::class.java
            }
        )
    }

    private fun containsAndroidPlatformType(type: Type): Boolean {
        val tokens = Regex("""[A-Za-z_$][A-Za-z0-9_$.]*""")
            .findAll(type.typeName)
            .map { it.value }
        return tokens.any { name ->
            name == "android" ||
                name.startsWith("android.") ||
                name == "androidx" ||
                name.startsWith("androidx.")
        }
    }
}
