package com.example.voice

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.model.SupportedLanguage
import com.example.data.model.VoiceCategory
import com.example.data.model.VoiceConfig
import com.example.data.model.VoiceInfo
import com.example.data.storage.VoicePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val preferences: VoicePreferences
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val voiceDiscovery = VoiceDiscovery()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeUtteranceId = MutableStateFlow<String?>(null)
    val activeUtteranceId: StateFlow<String?> = _activeUtteranceId.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<VoiceInfo>> = _availableVoices.asStateFlow()

    private val _currentVoice = MutableStateFlow<VoiceInfo?>(null)
    val currentVoice: StateFlow<VoiceInfo?> = _currentVoice.asStateFlow()

    private val _voiceConfig = MutableStateFlow(preferences.getVoiceConfig("default"))
    val voiceConfig: StateFlow<VoiceConfig> = _voiceConfig.asStateFlow()

    private val _languageList = MutableStateFlow<List<SupportedLanguage>>(emptyList())
    val languageList: StateFlow<List<SupportedLanguage>> = _languageList.asStateFlow()

    private val voiceHistoryStack = ArrayDeque<String>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupTtsEngine()
            _isInitialized.value = true
        } else {
            _isInitialized.value = false
        }
    }

    private fun setupTtsEngine() {
        val currentTts = tts ?: return

        currentTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _activeUtteranceId.value = utteranceId
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _activeUtteranceId.value = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _activeUtteranceId.value = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                _activeUtteranceId.value = null
            }
        })

        refreshLanguageSupport()
        refreshVoices(_voiceConfig.value.languageCode)
    }

    fun refreshLanguageSupport() {
        _languageList.value = voiceDiscovery.checkLanguageSupport(tts)
    }

    fun refreshVoices(targetLanguageCode: String) {
        val voices = voiceDiscovery.discoverVoices(tts, targetLanguageCode)
        _availableVoices.value = voices

        val savedId = _voiceConfig.value.selectedVoiceId
        val matchedVoice = voices.find { it.id == savedId }
            ?: voices.find { it.category == VoiceCategory.PROFESSIONAL }
            ?: voices.firstOrNull()

        _currentVoice.value = matchedVoice
        if (matchedVoice != null) {
            applyVoiceToEngine(matchedVoice, _voiceConfig.value)
        }
    }

    private fun applyVoiceToEngine(voiceInfo: VoiceInfo, config: VoiceConfig) {
        val currentTts = tts ?: return
        try {
            // Apply language
            currentTts.language = voiceInfo.locale

            // Try to assign exact system voice if found in tts.voices
            val sysVoices = currentTts.voices
            val actualSystemVoice = sysVoices?.find { it.name == voiceInfo.systemVoiceName }
            if (actualSystemVoice != null) {
                currentTts.voice = actualSystemVoice
            }

            // Apply pitch and rate (native to Android TTS)
            currentTts.setPitch(config.voicePitch.coerceIn(0.5f, 2.0f))
            currentTts.setSpeechRate(config.voiceSpeed.coerceIn(0.5f, 2.0f))
        } catch (_: Exception) {
            // Engine fallback
        }
    }

    fun selectVoice(voiceId: String, rememberInHistory: Boolean = true): Boolean {
        val target = _availableVoices.value.find { it.id == voiceId } ?: return false
        val previous = _currentVoice.value
        if (previous != null && rememberInHistory && previous.id != target.id) {
            voiceHistoryStack.addLast(previous.id)
            if (voiceHistoryStack.size > 10) voiceHistoryStack.removeFirst()
        }

        _currentVoice.value = target
        val newConfig = _voiceConfig.value.copy(
            selectedVoiceId = target.id,
            languageCode = target.locale.language
        )
        _voiceConfig.value = newConfig
        preferences.saveVoiceConfig(newConfig)
        applyVoiceToEngine(target, newConfig)
        return true
    }

    fun selectVoiceByCategory(category: VoiceCategory): VoiceInfo? {
        val target = _availableVoices.value.find { it.category == category }
            ?: _availableVoices.value.firstOrNull()
        if (target != null) {
            selectVoice(target.id)
        }
        return target
    }

    fun revertToPreviousVoice(): VoiceInfo? {
        val prevId = if (voiceHistoryStack.isNotEmpty()) {
            voiceHistoryStack.removeLast()
        } else {
            preferences.getPreviousVoiceId()
        }

        if (prevId != null) {
            val target = _availableVoices.value.find { it.id == prevId }
            if (target != null) {
                selectVoice(target.id, rememberInHistory = false)
                return target
            }
        }
        return null
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        val newConfig = _voiceConfig.value.copy(voiceSpeed = clamped)
        _voiceConfig.value = newConfig
        preferences.saveVoiceConfig(newConfig)
        tts?.setSpeechRate(clamped)
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        val newConfig = _voiceConfig.value.copy(voicePitch = clamped)
        _voiceConfig.value = newConfig
        preferences.saveVoiceConfig(newConfig)
        tts?.setPitch(clamped)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        val newConfig = _voiceConfig.value.copy(voiceVolume = clamped)
        _voiceConfig.value = newConfig
        preferences.saveVoiceConfig(newConfig)
    }

    fun setLanguage(languageCode: String) {
        val newConfig = _voiceConfig.value.copy(languageCode = languageCode)
        _voiceConfig.value = newConfig
        preferences.saveVoiceConfig(newConfig)
        refreshVoices(languageCode)
    }

    fun setAutoMatchPersonality(enabled: Boolean) {
        val newConfig = _voiceConfig.value.copy(autoMatchPersonality = enabled)
        _voiceConfig.value = newConfig
        preferences.saveVoiceConfig(newConfig)
    }

    fun previewVoice(voice: VoiceInfo? = null) {
        val targetVoice = voice ?: _currentVoice.value ?: return
        val lang = targetVoice.locale.language
        val sampleText = when (lang) {
            "ms" -> "Hai Boss, saya SANA. Seronok dapat berbual dengan awak."
            "ur" -> "ہیلو باس، میں ثناء ہوں۔ آپ سے بات کر کے اچھا لگا۔"
            "hi" -> "नमस्ते बॉस, मैं साना हूँ। आपसे बात करके अच्छा लगा।"
            else -> "Hi Boss, I'm SANA. It's nice to talk with you."
        }

        applyVoiceToEngine(targetVoice, _voiceConfig.value)
        speak(sampleText, utteranceId = "preview_${targetVoice.id}")
    }

    fun speak(text: String, utteranceId: String = "utterance_${System.currentTimeMillis()}") {
        val currentTts = tts ?: return
        if (!_isInitialized.value) return

        currentTts.stop()

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, _voiceConfig.value.voiceVolume)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        currentTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        _activeUtteranceId.value = null
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
