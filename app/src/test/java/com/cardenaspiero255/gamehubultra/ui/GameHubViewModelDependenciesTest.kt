package com.cardenaspiero255.gamehubultra.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameHubViewModelDependenciesTest {
    @Test
    fun dependencyFactoryContractIsAndroidFreeAndExplicit() {
        val factory = GameHubViewModelDependencyFactory::class.java
        val types = factory.methods.flatMap { method ->
            method.genericParameterTypes.toList() + method.genericReturnType
        }

        assertFalse(types.any { type -> type.typeName.contains("android.") })
        assertTrue(factory.methods.any { it.name == "create" })
    }
}
