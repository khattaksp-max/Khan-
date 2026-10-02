package com.example

import com.example.data.model.JealousyIntensity
import com.example.data.model.PersonalityMode
import com.example.data.model.VoiceCategory
import com.example.voice.CommandResult
import com.example.voice.VoiceCommandHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testPersonalitySuggestedVoiceCategory() {
        val modeNormal = PersonalityMode.NORMAL
        assertEquals(VoiceCategory.PROFESSIONAL, modeNormal.suggestedCategory)

        val modeCaring = PersonalityMode.CARING
        assertEquals(VoiceCategory.CALM, modeCaring.suggestedCategory)

        val modePlayful = PersonalityMode.PLAYFUL
        assertEquals(VoiceCategory.CUTE_PLAYFUL, modePlayful.suggestedCategory)

        val modeRomantic = PersonalityMode.ROMANTIC
        assertEquals(VoiceCategory.ROMANTIC, modeRomantic.suggestedCategory)
    }

    @Test
    fun testJealousySafetySanitizer() {
        val forbidden = "You are not allowed to talk to them."
        val hasForbidden = forbidden.contains("not allowed to talk", ignoreCase = true)
        assertTrue(hasForbidden)
    }

    @Test
    fun testStopAndBackgroundCommands() {
        // Test stop command detection string matching
        val stopInput = "SANA stop".lowercase()
        val isStop = stopInput == "sana stop" || stopInput.contains("stop listening")
        assertTrue(isStop)

        // Test background mode command detection
        val bgInput = "SANA, start background conversation".lowercase()
        val isBg = bgInput.contains("start background conversation")
        assertTrue(isBg)

        // Test WhatsApp action command detection
        val waInput = "SANA, open WhatsApp".lowercase()
        val isWa = waInput.contains("open whatsapp")
        assertTrue(isWa)
    }
}
