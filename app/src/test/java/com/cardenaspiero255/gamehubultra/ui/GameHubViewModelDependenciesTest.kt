package com.cardenaspiero255.gamehubultra.ui

import java.lang.reflect.Type
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameHubViewModelDependenciesTest {
    @Test
    fun dependencyFactoryContractIsAndroidFreeAndExplicit() {
        val factory = GameHubViewModelDependencyFactory::class.java
        val dependencyContract = GameHubViewModelDependencies::class.java
        val contracts = listOf(factory, dependencyContract)
        val exposedTypes = contracts.flatMap { contract ->
            contract.methods.flatMap { method ->
                method.genericParameterTypes.toList() + method.genericReturnType
            } + contract.genericInterfaces.toList()
        }

        assertFalse(
            exposedTypes.any(::containsAndroidPlatformType),
            "ViewModel dependency boundaries must not expose Android or AndroidX types"
        )
        assertTrue(factory.methods.any { it.name == "create" })
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
