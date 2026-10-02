package com.example.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.ai.GeminiAiService
import com.example.audio.SanaAudioManager
import com.example.conversation.SanaConversationManager
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.model.MicrophoneState
import com.example.data.storage.VoicePreferences
import com.example.device.AndroidActionHandler
import com.example.personality.PersonalityManager
import com.example.service.SanaVoiceForegroundService
import com.example.voice.CommandResult
import com.example.voice.VoiceCommandHandler
import com.example.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SanaSessionManager private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var instance: SanaSessionManager? = null

        fun getInstance(context: Context): SanaSessionManager {
            return instance ?: synchronized(this) {
                instance ?: SanaSessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    val preferences = VoicePreferences(appContext)
    val voiceManager = VoiceManager(appContext, preferences)
    val personalityManager = PersonalityManager(preferences, voiceManager)
    val actionHandler = AndroidActionHandler(appContext)
    val commandHandler = VoiceCommandHandler(voiceManager, personalityManager, actionHandler)
    val geminiService = GeminiAiService()

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = MessageSender.SANA,
                text = "Hi Boss! I'm SANA. Real microphone, real speaker voice output, and background conversation are ready. How can I assist you?",
                voiceUsed = "Initial"
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isBackgroundModeActive = MutableStateFlow(preferences.isBackgroundConversationEnabled())
    val isBackgroundModeActive: StateFlow<Boolean> = _isBackgroundModeActive.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val conversationManager: SanaConversationManager = SanaConversationManager(
        preferences = preferences,
        voiceManager = voiceManager,
        personalityManager = personalityManager,
        commandHandler = commandHandler,
        geminiService = geminiService,
        onMessageGenerated = { msg ->
            _messages.value = _messages.value + msg
        },
        onActionRequested = { result ->
            when (result) {
                is CommandResult.BackgroundModeToggled -> {
                    if (result.enabled) {
                        startBackgroundService()
                    } else {
                        stopForegroundService()
                    }
                }
                else -> {}
            }
        }
    )

    val audioManager: SanaAudioManager = SanaAudioManager(
        context = appContext,
        voiceManager = voiceManager,
        onSpeechRecognized = { recognizedText ->
            conversationManager.handleUserSpeech(recognizedText, _messages.value)
        },
        onBargeInDetected = {
            conversationManager.handleBargeIn()
        },
        onErrorReceived = { errMsg ->
            _statusMessage.value = errMsg
            val errorMsg = ChatMessage(
                sender = MessageSender.SANA,
                text = errMsg,
                voiceUsed = voiceManager.currentVoice.value?.displayName
            )
            _messages.value = _messages.value + errorMsg
        }
    )

    init {
        conversationManager.attachAudioManager(audioManager)
    }

    val microphoneState: StateFlow<MicrophoneState> = audioManager.microphoneState
    val audioRms: StateFlow<Float> = audioManager.audioRms
    val isConversationActive: StateFlow<Boolean> = conversationManager.isConversationActive
    val isThinking: StateFlow<Boolean> = conversationManager.isThinking

    fun toggleMicrophone() {
        if (!preferences.isMicrophoneEnabled()) {
            preferences.setMicrophoneEnabled(true)
        }

        if (isConversationActive.value || microphoneState.value == MicrophoneState.LISTENING) {
            conversationManager.stopConversation()
        } else {
            conversationManager.startConversation()
        }
    }

    fun startListening() {
        conversationManager.startConversation()
    }

    fun pauseListening() {
        audioManager.cancelListening()
    }

    fun resumeListening() {
        conversationManager.startConversation()
    }

    fun stopListening() {
        conversationManager.stopConversation()
    }

    fun startBackgroundService() {
        preferences.setBackgroundConversationEnabled(true)
        _isBackgroundModeActive.value = true
        SanaVoiceForegroundService.startService(appContext)
    }

    fun stopForegroundService() {
        preferences.setBackgroundConversationEnabled(false)
        _isBackgroundModeActive.value = false
        SanaVoiceForegroundService.stopService(appContext)
    }

    fun onForegroundServiceStarted() {
        _isBackgroundModeActive.value = true
        if (preferences.isContinuousListeningEnabled()) {
            startListening()
        }
    }

    fun onForegroundServiceStopped() {
        _isBackgroundModeActive.value = false
    }

    fun sendUserTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        conversationManager.handleUserSpeech(trimmed, _messages.value)
    }

    fun speakMessage(message: ChatMessage) {
        if (!preferences.isVoiceOutputEnabled()) return
        audioManager.onStartSpeaking()
        voiceManager.speak(message.text, utteranceId = message.id)
    }

    fun stopSpeaking() {
        voiceManager.stop()
        audioManager.onFinishSpeaking(
            shouldResumeListening = isConversationActive.value && preferences.isAutoResumeListeningEnabled(),
            languageLocale = voiceManager.currentVoice.value?.locale ?: java.util.Locale.US
        )
    }

    fun clearChat() {
        _messages.value = listOf(
            ChatMessage(
                sender = MessageSender.SANA,
                text = "Chat cleared! How can I assist you, Boss?",
                voiceUsed = voiceManager.currentVoice.value?.displayName
            )
        )
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
