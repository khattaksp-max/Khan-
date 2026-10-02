package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.model.ChatMessage
import com.example.data.model.JealousyIntensity
import com.example.data.model.MicrophoneState
import com.example.data.model.PersonalityMode
import com.example.data.model.SupportedLanguage
import com.example.data.model.VoiceConfig
import com.example.data.model.VoiceInfo
import com.example.data.storage.VoicePreferences
import com.example.session.SanaSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SanaViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SanaSessionManager.getInstance(application)
    val preferences: VoicePreferences = sessionManager.preferences

    val messages: StateFlow<List<ChatMessage>> = sessionManager.messages
    val isThinking: StateFlow<Boolean> = sessionManager.isThinking
    val microphoneState: StateFlow<MicrophoneState> = sessionManager.microphoneState
    val audioRms: StateFlow<Float> = sessionManager.audioRms
    val isConversationActive: StateFlow<Boolean> = sessionManager.isConversationActive
    val isBackgroundModeActive: StateFlow<Boolean> = sessionManager.isBackgroundModeActive
    val statusMessage: StateFlow<String?> = sessionManager.statusMessage

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isVoiceSettingsOpen = MutableStateFlow(false)
    val isVoiceSettingsOpen: StateFlow<Boolean> = _isVoiceSettingsOpen.asStateFlow()

    val availableVoices: StateFlow<List<VoiceInfo>> = sessionManager.voiceManager.availableVoices
    val currentVoice: StateFlow<VoiceInfo?> = sessionManager.voiceManager.currentVoice
    val voiceConfig: StateFlow<VoiceConfig> = sessionManager.voiceManager.voiceConfig
    val languageList: StateFlow<List<SupportedLanguage>> = sessionManager.voiceManager.languageList
    val isSpeaking: StateFlow<Boolean> = sessionManager.voiceManager.isSpeaking

    val currentPersonality: StateFlow<PersonalityMode> = sessionManager.personalityManager.currentPersonality
    val jealousyIntensity: StateFlow<JealousyIntensity> = sessionManager.personalityManager.jealousyIntensity

    // Continuous & Background settings states
    private val _isContinuousListening = MutableStateFlow(preferences.isContinuousListeningEnabled())
    val isContinuousListening: StateFlow<Boolean> = _isContinuousListening.asStateFlow()

    private val _isAutoResumeListening = MutableStateFlow(preferences.isAutoResumeListeningEnabled())
    val isAutoResumeListening: StateFlow<Boolean> = _isAutoResumeListening.asStateFlow()

    private val _isVoiceOutputEnabled = MutableStateFlow(preferences.isVoiceOutputEnabled())
    val isVoiceOutputEnabled: StateFlow<Boolean> = _isVoiceOutputEnabled.asStateFlow()

    private val _isMicrophoneEnabled = MutableStateFlow(preferences.isMicrophoneEnabled())
    val isMicrophoneEnabled: StateFlow<Boolean> = _isMicrophoneEnabled.asStateFlow()

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage(userText: String? = null) {
        val query = (userText ?: _inputText.value).trim()
        if (query.isEmpty()) return
        _inputText.value = ""
        sessionManager.sendUserTextMessage(query)
    }

    fun toggleMicrophone() {
        sessionManager.toggleMicrophone()
    }

    fun startListening() {
        sessionManager.startListening()
    }

    fun stopListening() {
        sessionManager.stopListening()
    }

    fun toggleBackgroundMode(enabled: Boolean) {
        if (enabled) {
            sessionManager.startBackgroundService()
        } else {
            sessionManager.stopForegroundService()
        }
    }

    fun setContinuousListening(enabled: Boolean) {
        _isContinuousListening.value = enabled
        preferences.setContinuousListeningEnabled(enabled)
    }

    fun setAutoResumeListening(enabled: Boolean) {
        _isAutoResumeListening.value = enabled
        preferences.setAutoResumeListeningEnabled(enabled)
    }

    fun setVoiceOutputEnabled(enabled: Boolean) {
        _isVoiceOutputEnabled.value = enabled
        preferences.setVoiceOutputEnabled(enabled)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        _isMicrophoneEnabled.value = enabled
        preferences.setMicrophoneEnabled(enabled)
        if (!enabled) {
            sessionManager.stopListening()
        }
    }

    fun previewVoice(voice: VoiceInfo? = null) {
        sessionManager.voiceManager.previewVoice(voice)
    }

    fun selectVoice(voiceId: String) {
        sessionManager.voiceManager.selectVoice(voiceId)
    }

    fun setSpeed(speed: Float) {
        sessionManager.voiceManager.setSpeed(speed)
    }

    fun setPitch(pitch: Float) {
        sessionManager.voiceManager.setPitch(pitch)
    }

    fun setVolume(volume: Float) {
        sessionManager.voiceManager.setVolume(volume)
    }

    fun setLanguage(langCode: String) {
        sessionManager.voiceManager.setLanguage(langCode)
    }

    fun setPersonality(mode: PersonalityMode) {
        sessionManager.personalityManager.setPersonality(mode)
    }

    fun setJealousyIntensity(intensity: JealousyIntensity) {
        sessionManager.personalityManager.setJealousyIntensity(intensity)
    }

    fun setAutoMatchPersonality(enabled: Boolean) {
        sessionManager.voiceManager.setAutoMatchPersonality(enabled)
    }

    fun openVoiceSettings() {
        _isVoiceSettingsOpen.value = true
    }

    fun closeVoiceSettings() {
        _isVoiceSettingsOpen.value = false
    }

    fun speakMessage(message: ChatMessage) {
        sessionManager.speakMessage(message)
    }

    fun stopSpeaking() {
        sessionManager.stopSpeaking()
    }

    fun clearChat() {
        sessionManager.clearChat()
    }

    fun clearStatusMessage() {
        sessionManager.clearStatusMessage()
    }
}
