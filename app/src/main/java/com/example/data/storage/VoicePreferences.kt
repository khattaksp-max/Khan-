package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.JealousyIntensity
import com.example.data.model.PersonalityMode
import com.example.data.model.VoiceConfig

class VoicePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sana_voice_memory_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SELECTED_VOICE_ID = "selected_voice_id"
        private const val KEY_PREVIOUS_VOICE_ID = "previous_voice_id"
        private const val KEY_VOICE_SPEED = "voice_speed"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_VOICE_VOLUME = "voice_volume"
        private const val KEY_LANGUAGE_CODE = "language_code"
        private const val KEY_AUTO_MATCH = "auto_match_personality"
        private const val KEY_PERSONALITY_MODE = "personality_mode"
        private const val KEY_JEALOUSY_INTENSITY = "jealousy_intensity"
        private const val KEY_AUTO_SPEAK = "auto_speak_responses"
        private const val KEY_BACKGROUND_CONVERSATION = "background_conversation_enabled"
        private const val KEY_CONTINUOUS_LISTENING = "continuous_listening_enabled"
        private const val KEY_AUTO_RESUME_LISTENING = "auto_resume_listening_enabled"
        private const val KEY_VOICE_OUTPUT = "voice_output_enabled"
        private const val KEY_MICROPHONE_ENABLED = "microphone_enabled"
    }

    fun getVoiceConfig(defaultVoiceId: String): VoiceConfig {
        return VoiceConfig(
            selectedVoiceId = prefs.getString(KEY_SELECTED_VOICE_ID, defaultVoiceId) ?: defaultVoiceId,
            voiceSpeed = prefs.getFloat(KEY_VOICE_SPEED, 1.0f),
            voicePitch = prefs.getFloat(KEY_VOICE_PITCH, 1.0f),
            voiceVolume = prefs.getFloat(KEY_VOICE_VOLUME, 1.0f),
            languageCode = prefs.getString(KEY_LANGUAGE_CODE, "en") ?: "en",
            autoMatchPersonality = prefs.getBoolean(KEY_AUTO_MATCH, true),
            isPitchSupported = true
        )
    }

    fun saveVoiceConfig(config: VoiceConfig) {
        val currentVoice = prefs.getString(KEY_SELECTED_VOICE_ID, null)
        val editor = prefs.edit()
        if (currentVoice != null && currentVoice != config.selectedVoiceId) {
            editor.putString(KEY_PREVIOUS_VOICE_ID, currentVoice)
        }
        editor.putString(KEY_SELECTED_VOICE_ID, config.selectedVoiceId)
            .putFloat(KEY_VOICE_SPEED, config.voiceSpeed)
            .putFloat(KEY_VOICE_PITCH, config.voicePitch)
            .putFloat(KEY_VOICE_VOLUME, config.voiceVolume)
            .putString(KEY_LANGUAGE_CODE, config.languageCode)
            .putBoolean(KEY_AUTO_MATCH, config.autoMatchPersonality)
            .apply()
    }

    fun getPreviousVoiceId(): String? {
        return prefs.getString(KEY_PREVIOUS_VOICE_ID, null)
    }

    fun recordVoiceChange(newVoiceId: String) {
        val current = prefs.getString(KEY_SELECTED_VOICE_ID, null)
        val editor = prefs.edit()
        if (current != null && current != newVoiceId) {
            editor.putString(KEY_PREVIOUS_VOICE_ID, current)
        }
        editor.putString(KEY_SELECTED_VOICE_ID, newVoiceId).apply()
    }

    fun getPersonalityMode(): PersonalityMode {
        val name = prefs.getString(KEY_PERSONALITY_MODE, PersonalityMode.NORMAL.name)
        return try {
            PersonalityMode.valueOf(name ?: PersonalityMode.NORMAL.name)
        } catch (_: Exception) {
            PersonalityMode.NORMAL
        }
    }

    fun savePersonalityMode(mode: PersonalityMode) {
        prefs.edit().putString(KEY_PERSONALITY_MODE, mode.name).apply()
    }

    fun getJealousyIntensity(): JealousyIntensity {
        val name = prefs.getString(KEY_JEALOUSY_INTENSITY, JealousyIntensity.OFF.name)
        return try {
            JealousyIntensity.valueOf(name ?: JealousyIntensity.OFF.name)
        } catch (_: Exception) {
            JealousyIntensity.OFF
        }
    }

    fun saveJealousyIntensity(intensity: JealousyIntensity) {
        prefs.edit().putString(KEY_JEALOUSY_INTENSITY, intensity.name).apply()
    }

    fun getAutoSpeak(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SPEAK, true)
    }

    fun setAutoSpeak(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SPEAK, enabled).apply()
    }

    fun isBackgroundConversationEnabled(): Boolean {
        return prefs.getBoolean(KEY_BACKGROUND_CONVERSATION, false)
    }

    fun setBackgroundConversationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_CONVERSATION, enabled).apply()
    }

    fun isContinuousListeningEnabled(): Boolean {
        return prefs.getBoolean(KEY_CONTINUOUS_LISTENING, true)
    }

    fun setContinuousListeningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTINUOUS_LISTENING, enabled).apply()
    }

    fun isAutoResumeListeningEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_RESUME_LISTENING, true)
    }

    fun setAutoResumeListeningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RESUME_LISTENING, enabled).apply()
    }

    fun isVoiceOutputEnabled(): Boolean {
        return prefs.getBoolean(KEY_VOICE_OUTPUT, true)
    }

    fun setVoiceOutputEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_OUTPUT, enabled).apply()
    }

    fun isMicrophoneEnabled(): Boolean {
        return prefs.getBoolean(KEY_MICROPHONE_ENABLED, true)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MICROPHONE_ENABLED, enabled).apply()
    }
}
