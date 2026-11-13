package com.jarvis.assistant.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiyaCloseCommandsTest {

    @Test
    fun testExactEnglishAndHinglishCommands() {
        assertTrue(SiyaCloseCommands.isCloseCommand("sleep"))
        assertTrue(SiyaCloseCommands.isCloseCommand("so jao"))
        assertTrue(SiyaCloseCommands.isCloseCommand("band ho jao"))
        assertTrue(SiyaCloseCommands.isCloseCommand("sleep mode"))
        assertTrue(SiyaCloseCommands.isCloseCommand("chale jao"))
        assertTrue(SiyaCloseCommands.isCloseCommand("band karo"))
        assertTrue(SiyaCloseCommands.isCloseCommand("bye"))
        assertTrue(SiyaCloseCommands.isCloseCommand("good night"))
    }

    @Test
    fun testExactDevanagariCommands() {
        assertTrue(SiyaCloseCommands.isCloseCommand("सो जाओ"))
        assertTrue(SiyaCloseCommands.isCloseCommand("बंद हो जाओ"))
        assertTrue(SiyaCloseCommands.isCloseCommand("बंद करो"))
        assertTrue(SiyaCloseCommands.isCloseCommand("चले जाओ"))
        assertTrue(SiyaCloseCommands.isCloseCommand("शुभ रात्रि"))
    }

    @Test
    fun testPunctuationAndWhitespaceNormalization() {
        assertTrue(SiyaCloseCommands.isCloseCommand("  sleep!  "))
        assertTrue(SiyaCloseCommands.isCloseCommand("So Jao, please."))
        assertTrue(SiyaCloseCommands.isCloseCommand("bye!"))
        assertTrue(SiyaCloseCommands.isCloseCommand("शुभ रात्रि..."))
        assertTrue(SiyaCloseCommands.isCloseCommand("सो जाओ!"))
        assertTrue(SiyaCloseCommands.isCloseCommand("chale jao!"))
    }

    @Test
    fun testShortPhrasesUnderFiveWords() {
        assertTrue(SiyaCloseCommands.isCloseCommand("ok siya sleep"))
        assertTrue(SiyaCloseCommands.isCloseCommand("siya so jao ab"))
        assertTrue(SiyaCloseCommands.isCloseCommand("ok bye siya"))
        assertTrue(SiyaCloseCommands.isCloseCommand("achha siya band karo"))
        assertTrue(SiyaCloseCommands.isCloseCommand("सिया सो जाओ"))
        assertTrue(SiyaCloseCommands.isCloseCommand("सिया शुभ रात्रि"))
    }

    @Test
    fun testLongSentencesRejected() {
        // More than 5 words should NOT trigger a close command even if "sleep" or "bye" is inside
        assertFalse(SiyaCloseCommands.isCloseCommand("I want to tell him bye tomorrow morning"))
        assertFalse(SiyaCloseCommands.isCloseCommand("sleep is very important for healthy living everyday"))
        assertFalse(SiyaCloseCommands.isCloseCommand("aaj raat ko jaldi so jao warna subah late ho jaoge"))
    }

    @Test
    fun testSubstringNotMatched() {
        // Substrings of larger words should not match
        assertFalse(SiyaCloseCommands.isCloseCommand("sleepless"))
        assertFalse(SiyaCloseCommands.isCloseCommand("sleeping"))
        assertFalse(SiyaCloseCommands.isCloseCommand("byelaw"))
        assertFalse(SiyaCloseCommands.isCloseCommand("asleep"))
    }

    @Test
    fun testEmptyAndNull() {
        assertFalse(SiyaCloseCommands.isCloseCommand(null))
        assertFalse(SiyaCloseCommands.isCloseCommand(""))
        assertFalse(SiyaCloseCommands.isCloseCommand("   "))
    }
}
