package com.example.data.model

import java.util.Locale

enum class VoiceCategory(val label: String, val description: String) {
    WARM("Warm", "Soft, friendly, and natural"),
    CALM("Calm", "Gentle, relaxed, and caring"),
    ENERGETIC("Energetic", "Bright, enthusiastic, and fun"),
    PROFESSIONAL("Professional", "Clear, confident, and assistant-like"),
    CUTE_PLAYFUL("Cute / Playful", "Sweet, cheerful, and lighthearted"),
    ROMANTIC("Romantic", "Warm, affectionate, and soft")
}

data class VoiceInfo(
    val id: String,
    val displayName: String,
    val systemVoiceName: String,
    val category: VoiceCategory,
    val description: String,
    val locale: Locale,
    val isNetworkConnectionRequired: Boolean = false,
    val defaultPitch: Float = 1.0f,
    val defaultSpeed: Float = 1.0f
)

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val locale: Locale,
    val isAvailable: Boolean = true,
    val statusText: String = "Available"
)

enum class PersonalityMode(val displayName: String, val description: String, val suggestedCategory: VoiceCategory) {
    NORMAL("Normal", "Natural, balanced, and professional assistant", VoiceCategory.PROFESSIONAL),
    FRIENDLY("Friendly", "Warm, welcoming, and open conversationalist", VoiceCategory.WARM),
    CARING("Caring", "Gentle, calm, and soothing companion", VoiceCategory.CALM),
    PLAYFUL("Playful", "Sweet, cheerful, lighthearted, and fun", VoiceCategory.CUTE_PLAYFUL),
    ROMANTIC("Romantic", "Warm, affectionate, and soft-spoken companion", VoiceCategory.ROMANTIC),
    PLAYFUL_JEALOUS("Playful Jealous", "Cute, teasing banter with playful AI fictional jealousy", VoiceCategory.CUTE_PLAYFUL)
}

enum class JealousyIntensity(val displayName: String, val description: String) {
    OFF("OFF", "No jealousy teasing"),
    LIGHT("LIGHT", "Subtle, cute, teasing remarks"),
    PLAYFUL("PLAYFUL", "Fun, cheeky banter & adorable jealousy"),
    EXTRA_PLAYFUL("EXTRA PLAYFUL", "Maximum witty teasing & dramatic cute banter")
}

data class VoiceConfig(
    val selectedVoiceId: String,
    val voiceSpeed: Float = 1.0f,      // 0.5f to 2.0f
    val voicePitch: Float = 1.0f,      // 0.5f to 2.0f
    val voiceVolume: Float = 1.0f,     // 0.0f to 1.0f (0 - 100%)
    val languageCode: String = "en",
    val autoMatchPersonality: Boolean = true,
    val isPitchSupported: Boolean = true
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val voiceUsed: String? = null,
    val isTeasing: Boolean = false,
    val isPlayingAudio: Boolean = false
)

enum class MessageSender {
    USER,
    SANA
}

enum class MicrophoneState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

data class VoiceSettingsState(
    val isBackgroundConversationEnabled: Boolean = false,
    val isContinuousListeningEnabled: Boolean = true,
    val isAutoResumeListeningEnabled: Boolean = true,
    val isVoiceOutputEnabled: Boolean = true,
    val isMicrophoneEnabled: Boolean = true
)
