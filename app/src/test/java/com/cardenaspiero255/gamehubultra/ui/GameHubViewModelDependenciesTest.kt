package com.cardenaspiero255.gamehubultra.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameHubViewModelDependenciesTest {
    @Test
    fun dependencyFactoryContractIsAndroidFreeAndExplicit() {
        val factory = GameHubViewModelDependencyFactory::class.java
        val dependencyContract = GameHubViewModelDependencies::class.java
        val exposedTypes = (factory.methods + dependencyContract.methods)
            .flatMap { method ->
                method.genericParameterTypes.toList() + method.genericReturnType
            } + dependencyContract.interfaces.toList()

        assertFalse(
            exposedTypes.any { type -> type.typeName.contains("android.") },
            "ViewModel dependency boundaries must not expose Android types"
        )
        assertTrue(factory.methods.any { it.name == "create" })
    }
}
