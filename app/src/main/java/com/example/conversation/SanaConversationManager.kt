package com.example.conversation

import com.example.ai.GeminiAiService
import com.example.audio.SanaAudioManager
import com.example.data.model.ChatMessage
import com.example.data.model.JealousyIntensity
import com.example.data.model.MessageSender
import com.example.data.storage.VoicePreferences
import com.example.personality.PersonalityManager
import com.example.voice.CommandResult
import com.example.voice.VoiceCommandHandler
import com.example.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SanaConversationManager(
    private val preferences: VoicePreferences,
    private val voiceManager: VoiceManager,
    private val personalityManager: PersonalityManager,
    private val commandHandler: VoiceCommandHandler,
    private val geminiService: GeminiAiService,
    private val onMessageGenerated: (ChatMessage) -> Unit,
    private val onActionRequested: (CommandResult) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var audioManager: SanaAudioManager? = null

    private val _isConversationActive = MutableStateFlow(false)
    val isConversationActive: StateFlow<Boolean> = _isConversationActive.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    fun attachAudioManager(audioMgr: SanaAudioManager) {
        this.audioManager = audioMgr
    }

    fun startConversation() {
        if (!preferences.isMicrophoneEnabled()) return
        _isConversationActive.value = true
        val locale = voiceManager.currentVoice.value?.locale ?: java.util.Locale.US
        audioManager?.startListening(locale)
    }

    fun stopConversation() {
        _isConversationActive.value = false
        audioManager?.cancelListening()
        voiceManager.stop()
    }

    fun handleBargeIn() {
        // User interrupted SANA while speaking
        voiceManager.stop()
        if (_isConversationActive.value && preferences.isContinuousListeningEnabled()) {
            val locale = voiceManager.currentVoice.value?.locale ?: java.util.Locale.US
            audioManager?.startListening(locale)
        }
    }

    fun handleUserSpeech(userInput: String, history: List<ChatMessage>) {
        val trimmed = userInput.trim()
        if (trimmed.isEmpty()) return

        // Post user message
        val userMsg = ChatMessage(sender = MessageSender.USER, text = trimmed)
        onMessageGenerated(userMsg)

        // 1. Process voice and device commands
        when (val result = commandHandler.processCommand(trimmed)) {
            is CommandResult.StopListening -> {
                _isConversationActive.value = false
                val sanaMsg = ChatMessage(
                    sender = MessageSender.SANA,
                    text = result.replyText,
                    voiceUsed = voiceManager.currentVoice.value?.displayName
                )
                onMessageGenerated(sanaMsg)
                speakAndFollowUp(result.replyText, sanaMsg.id, resumeListening = false)
                onActionRequested(result)
                return
            }
            is CommandResult.BackgroundModeToggled -> {
                preferences.setBackgroundConversationEnabled(result.enabled)
                val sanaMsg = ChatMessage(
                    sender = MessageSender.SANA,
                    text = result.replyText,
                    voiceUsed = voiceManager.currentVoice.value?.displayName
                )
                onMessageGenerated(sanaMsg)
                speakAndFollowUp(result.replyText, sanaMsg.id, resumeListening = _isConversationActive.value)
                onActionRequested(result)
                return
            }
            is CommandResult.DeviceActionExecuted -> {
                val sanaMsg = ChatMessage(
                    sender = MessageSender.SANA,
                    text = result.replyText,
                    voiceUsed = voiceManager.currentVoice.value?.displayName
                )
                onMessageGenerated(sanaMsg)
                speakAndFollowUp(result.replyText, sanaMsg.id, resumeListening = _isConversationActive.value)
                onActionRequested(result)
                return
            }
            is CommandResult.VoiceChanged -> {
                val sanaMsg = ChatMessage(
                    sender = MessageSender.SANA,
                    text = result.replyText,
                    voiceUsed = result.voiceName
                )
                onMessageGenerated(sanaMsg)
                speakAndFollowUp(result.replyText, sanaMsg.id, resumeListening = _isConversationActive.value)
                onActionRequested(result)
                return
            }
            is CommandResult.ShowVoiceSettings -> {
                val sanaMsg = ChatMessage(
                    sender = MessageSender.SANA,
                    text = result.replyText,
                    voiceUsed = voiceManager.currentVoice.value?.displayName
                )
                onMessageGenerated(sanaMsg)
                speakAndFollowUp(result.replyText, sanaMsg.id, resumeListening = false)
                onActionRequested(result)
                return
            }
            is CommandResult.JealousyChanged -> {
                val sanaMsg = ChatMessage(
                    sender = MessageSender.SANA,
                    text = result.replyText,
                    voiceUsed = voiceManager.currentVoice.value?.displayName,
                    isTeasing = result.intensity != JealousyIntensity.OFF
                )
                onMessageGenerated(sanaMsg)
                speakAndFollowUp(result.replyText, sanaMsg.id, resumeListening = _isConversationActive.value)
                onActionRequested(result)
                return
            }
            is CommandResult.NotACommand -> {
                // Proceed with conversational generation
            }
        }

        // 2. Query Gemini / Companion AI
        scope.launch {
            _isThinking.value = true
            val historyPairs = history.map {
                (if (it.sender == MessageSender.USER) "USER" else "MODEL") to it.text
            }
            val systemPrompt = personalityManager.generateSystemInstruction()

            val rawReply = geminiService.getReply(
                userPrompt = trimmed,
                history = historyPairs,
                systemInstructionText = systemPrompt,
                personalityMode = personalityManager.currentPersonality.value,
                jealousyIntensity = personalityManager.jealousyIntensity.value
            )

            val safeReply = personalityManager.sanitizeOutput(rawReply)
            _isThinking.value = false

            val isTease = personalityManager.jealousyIntensity.value != JealousyIntensity.OFF &&
                    (safeReply.contains("😏") || safeReply.contains("jealous") || safeReply.contains("Boss"))

            val activeVoiceName = voiceManager.currentVoice.value?.displayName ?: "SANA Voice"
            val sanaMsg = ChatMessage(
                sender = MessageSender.SANA,
                text = safeReply,
                voiceUsed = activeVoiceName,
                isTeasing = isTease
            )
            onMessageGenerated(sanaMsg)

            val shouldContinue = _isConversationActive.value && preferences.isContinuousListeningEnabled()
            speakAndFollowUp(safeReply, sanaMsg.id, resumeListening = shouldContinue)
        }
    }

    private fun speakAndFollowUp(text: String, utteranceId: String, resumeListening: Boolean) {
        if (!preferences.isVoiceOutputEnabled()) {
            if (resumeListening && preferences.isAutoResumeListeningEnabled()) {
                val locale = voiceManager.currentVoice.value?.locale ?: java.util.Locale.US
                audioManager?.startListening(locale)
            }
            return
        }

        audioManager?.onStartSpeaking()
        voiceManager.speak(text, utteranceId = utteranceId)

        // Observe voiceManager speaking completion to re-engage listening
        scope.launch {
            // Poll completion cleanly
            while (voiceManager.isSpeaking.value) {
                kotlinx.coroutines.delay(100)
            }
            val locale = voiceManager.currentVoice.value?.locale ?: java.util.Locale.US
            audioManager?.onFinishSpeaking(
                shouldResumeListening = resumeListening && preferences.isAutoResumeListeningEnabled(),
                languageLocale = locale
            )
        }
    }
}
