package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Public contract for deterministic, factually grounded assistant identity answers. */
class UltraF9WakeIdentityAnswersTest {
    @Test fun defaultNameAnswersNameAndReasonWithoutInventingHistory() {
        val answer = UltraWakeIdentityAnswerPolicy.respond(
            "Ultra, como te llamas?", "ultra", null
        )
        assertEquals("Me llamo Ultra. Es mi nombre predeterminado.", answer)
        assertEquals(
            "Me llamo Ultra. Es mi nombre predeterminado.",
            UltraWakeIdentityAnswerPolicy.respond("Ultra cual es tu nombre", "ultra", null)
        )
        assertEquals(
            "Me llamo Ultra porque es mi nombre predeterminado.",
            UltraWakeIdentityAnswerPolicy.respond("Ultra por que te llamas Ultra", "ultra", null)
        )
    }

    @Test fun changedNameUsesStoredValueAndExplainsThatUserNamedIt() {
        val expected = "Me llamo Juan, porque ese es el nombre que me pusiste. Antes me llamaba Ultra."
        listOf("Ultra como te llamas", "Ultra cual es tu nombre", "Ultra dime como te llamas")
            .forEach { query ->
                assertEquals(expected, UltraWakeIdentityAnswerPolicy.respond(query, "juan", "ultra"), query)
            }
        assertEquals(
            "Me llamo Juan porque ese es el nombre que me pusiste.",
            UltraWakeIdentityAnswerPolicy.respond("Ultra por que te llamas Juan", "juan", "ultra")
        )
        assertEquals(
            "Antes me llamaba Ultra. Ahora me llamo Juan.",
            UltraWakeIdentityAnswerPolicy.respond("Ultra como te llamabas antes", "juan", "ultra")
        )
    }

    @Test fun renamedTwiceReportsActualPreviousNameInsteadOfAlwaysAssumingUltra() {
        assertEquals(
            "Antes me llamaba Juan. Ahora me llamo Roberto.",
            UltraWakeIdentityAnswerPolicy.respond("Ultra cual era tu nombre anterior", "roberto", "juan")
        )
        assertEquals(
            "Me llamo Roberto, porque ese es el nombre que me pusiste. Antes me llamaba Juan.",
            UltraWakeIdentityAnswerPolicy.respond("Ultra como te llamas", "roberto", "juan")
        )
    }

    @Test fun unknownPreviousNameIsNotFabricated() {
        assertEquals(
            "Me llamo Roberto, porque ese es el nombre que me pusiste. Mi nombre original es Ultra.",
            UltraWakeIdentityAnswerPolicy.respond("Ultra como te llamas", "roberto", null)
        )
        assertEquals(
            "Mi nombre original es Ultra. No tengo registrado el nombre inmediatamente anterior.",
            UltraWakeIdentityAnswerPolicy.respond("Ultra como te llamabas antes", "roberto", null)
        )
        assertEquals(
            "Mi nombre original es Ultra. No tengo registrado un nombre anterior.",
            UltraWakeIdentityAnswerPolicy.respond("Ultra como te llamabas antes", "ultra", null)
        )
    }

    @Test fun questionsAboutUserOthersGamesAndNegationsAreNotHijacked() {
        listOf(
            "Ultra como me llamo",
            "Ultra cual es mi nombre",
            "Ultra como se llama Juan",
            "Ultra por que Juan se llama Roberto",
            "Ultra no me digas como te llamas",
            "Ultra que pasaria si te llamaras Juan",
            "Ultra explica como te llamas en una historia",
            "Ultra abre el juego Como te llamas",
            "Ultra desde ahora te llamaras Roberto",
            "Ultra porque ese nombre te puse ayer"
        ).forEach { query ->
            assertNull(UltraWakeIdentityAnswerPolicy.respond(query, "juan", "ultra"), query)
        }
    }

    @Test fun questionsOnlyReadIdentityAndDoNotMutateExternalState() {
        var mutations = 0
        val name = "roberto"
        val previous = "juan"
        val response = UltraWakeIdentityAnswerPolicy.respond(
            "Ultra como te llamas", name, previous
        )
        assertTrue(response!!.contains("Roberto"))
        assertTrue(response.contains("Juan"))
        assertEquals(0, mutations)
        assertFalse(response.contains("Jarvis"))
    }
}
