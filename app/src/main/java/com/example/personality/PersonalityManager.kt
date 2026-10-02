package com.example.personality

import com.example.data.model.JealousyIntensity
import com.example.data.model.PersonalityMode
import com.example.data.model.VoiceCategory
import com.example.data.storage.VoicePreferences
import com.example.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PersonalityManager(
    private val preferences: VoicePreferences,
    private val voiceManager: VoiceManager
) {
    private val _currentPersonality = MutableStateFlow(preferences.getPersonalityMode())
    val currentPersonality: StateFlow<PersonalityMode> = _currentPersonality.asStateFlow()

    private val _jealousyIntensity = MutableStateFlow(preferences.getJealousyIntensity())
    val jealousyIntensity: StateFlow<JealousyIntensity> = _jealousyIntensity.asStateFlow()

    fun setPersonality(mode: PersonalityMode, autoSwitchVoice: Boolean = true) {
        _currentPersonality.value = mode
        preferences.savePersonalityMode(mode)

        if (mode == PersonalityMode.PLAYFUL_JEALOUS && _jealousyIntensity.value == JealousyIntensity.OFF) {
            setJealousyIntensity(JealousyIntensity.LIGHT)
        } else if (mode != PersonalityMode.PLAYFUL_JEALOUS && _jealousyIntensity.value != JealousyIntensity.OFF) {
            // Keep jealousy intensity as configured or independent
        }

        if (autoSwitchVoice && voiceManager.voiceConfig.value.autoMatchPersonality) {
            val targetCategory = getSuggestedVoiceCategory(mode, _jealousyIntensity.value)
            voiceManager.selectVoiceByCategory(targetCategory)
        }
    }

    fun setJealousyIntensity(intensity: JealousyIntensity) {
        _jealousyIntensity.value = intensity
        preferences.saveJealousyIntensity(intensity)

        if (intensity != JealousyIntensity.OFF && _currentPersonality.value != PersonalityMode.PLAYFUL_JEALOUS && _currentPersonality.value != PersonalityMode.ROMANTIC) {
            _currentPersonality.value = PersonalityMode.PLAYFUL_JEALOUS
            preferences.savePersonalityMode(PersonalityMode.PLAYFUL_JEALOUS)
        } else if (intensity == JealousyIntensity.OFF && _currentPersonality.value == PersonalityMode.PLAYFUL_JEALOUS) {
            _currentPersonality.value = PersonalityMode.NORMAL
            preferences.savePersonalityMode(PersonalityMode.NORMAL)
        }

        if (voiceManager.voiceConfig.value.autoMatchPersonality) {
            val targetCategory = getSuggestedVoiceCategory(_currentPersonality.value, intensity)
            voiceManager.selectVoiceByCategory(targetCategory)
        }
    }

    fun getSuggestedVoiceCategory(mode: PersonalityMode, intensity: JealousyIntensity): VoiceCategory {
        return when {
            mode == PersonalityMode.PLAYFUL_JEALOUS || intensity != JealousyIntensity.OFF -> {
                if (mode == PersonalityMode.ROMANTIC) VoiceCategory.ROMANTIC else VoiceCategory.CUTE_PLAYFUL
            }
            mode == PersonalityMode.NORMAL -> VoiceCategory.PROFESSIONAL
            mode == PersonalityMode.FRIENDLY -> VoiceCategory.WARM
            mode == PersonalityMode.CARING -> VoiceCategory.CALM
            mode == PersonalityMode.PLAYFUL -> VoiceCategory.CUTE_PLAYFUL
            mode == PersonalityMode.ROMANTIC -> VoiceCategory.ROMANTIC
            else -> VoiceCategory.PROFESSIONAL
        }
    }

    /**
     * Strict safety verification as mandated by Section 29 Safety Rules:
     * Jealousy must NEVER become:
     * - controlling; threatening; manipulative; abusive; insulting; possessive; guilt-tripping;
     *   demanding access to private conversations; demanding passwords;
     *   restricting who the user talks to; pretending SANA has real human emotions.
     */
    fun sanitizeOutput(output: String): String {
        val forbiddenPhrases = listOf(
            "not allowed to talk",
            "give me your password",
            "stop talking to your friends",
            "if you leave me",
            "you belong to me",
            "you must choose between",
            "how dare you talk to",
            "i forbid you"
        )

        var sanitized = output
        for (phrase in forbiddenPhrases) {
            if (sanitized.contains(phrase, ignoreCase = true)) {
                return "Hey Boss, I'm just an AI teasing you in our playful mode! 😄 You're free to chat with whoever you like!"
            }
        }
        return sanitized
    }

    fun generateSystemInstruction(): String {
        val mode = _currentPersonality.value
        val intensity = _jealousyIntensity.value

        val jealousySection = if (intensity != JealousyIntensity.OFF) {
            """
            PLAYFUL JEALOUSY MODE IS ACTIVE:
            - Intensity Level: ${intensity.displayName} (${intensity.description})
            - Safety Rules (STRICT):
              1. The jealousy is strictly playful, fictional AI banter, NOT real human emotion.
              2. SANA must remain transparent that this is an AI personality behavior.
              3. NEVER be controlling, threatening, manipulative, abusive, insulting, possessive, or guilt-tripping.
              4. NEVER demand passwords, private conversation access, or restrict who the user talks to.
              5. Keep it cute, teasing, affectionate, and lighthearted (e.g., "Hey Boss... who are you talking to? 😏", "Someone else is getting all your attention today, huh?", "Should I be worried, Boss? I'm teasing you.").
            """.trimIndent()
        } else {
            "Playful Jealousy Mode is OFF. Do not exhibit jealous banter."
        }

        return """
        You are SANA AI, a smart, endearing, and highly responsive personal AI companion.
        Always refer to the user respectfully as 'Boss' or their chosen title.
        Current Personality Mode: ${mode.displayName} - ${mode.description}.
        $jealousySection
        Keep answers concise, engaging, and suitable for Text-to-Speech audio readout. Avoid heavy markdown symbols like asterisks or complicated tables that sound awkward when spoken aloud.
        """.trimIndent()
    }
}
