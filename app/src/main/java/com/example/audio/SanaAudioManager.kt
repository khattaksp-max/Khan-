package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.data.model.MicrophoneState
import com.example.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SanaAudioManager(
    private val context: Context,
    private val voiceManager: VoiceManager,
    private val onSpeechRecognized: (String) -> Unit,
    private val onBargeInDetected: () -> Unit,
    private val onErrorReceived: (String) -> Unit
) : RecognitionListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningDesired = false
    private var isMutedForOutput = false

    private val _microphoneState = MutableStateFlow(MicrophoneState.IDLE)
    val microphoneState: StateFlow<MicrophoneState> = _microphoneState.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Another app or phone call took audio focus
                voiceManager.stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck volume if desired
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Audio focus restored
            }
        }
    }

    init {
        mainHandler.post {
            initSpeechRecognizer()
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _microphoneState.value = MicrophoneState.ERROR
            onErrorReceived("Boss, speech recognition service is not available on this device.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(this@SanaAudioManager)
            }
        } catch (e: Exception) {
            _microphoneState.value = MicrophoneState.ERROR
            onErrorReceived("Boss, I can't access the microphone right now.")
        }
    }

    fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun startListening(languageLocale: Locale = Locale.US) {
        isListeningDesired = true
        if (isMutedForOutput) {
            // SANA is currently speaking, do not open mic yet to prevent feedback
            return
        }

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer?.startListening(intent)
                _microphoneState.value = MicrophoneState.LISTENING
            } catch (e: Exception) {
                _microphoneState.value = MicrophoneState.ERROR
                onErrorReceived("Boss, I can't access the microphone right now.")
            }
        }
    }

    fun stopListening() {
        isListeningDesired = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                if (_microphoneState.value == MicrophoneState.LISTENING) {
                    _microphoneState.value = MicrophoneState.IDLE
                }
            } catch (_: Exception) {}
        }
    }

    fun cancelListening() {
        isListeningDesired = false
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                if (_microphoneState.value != MicrophoneState.SPEAKING) {
                    _microphoneState.value = MicrophoneState.IDLE
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Called before SANA speaks. Stops microphone listener to prevent echo feedback.
     */
    fun onStartSpeaking() {
        isMutedForOutput = true
        _microphoneState.value = MicrophoneState.SPEAKING
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
        }
        requestAudioFocus()
    }

    /**
     * Called when SANA finishes speaking.
     */
    fun onFinishSpeaking(shouldResumeListening: Boolean, languageLocale: Locale = Locale.US) {
        isMutedForOutput = false
        abandonAudioFocus()
        if (shouldResumeListening && isListeningDesired) {
            mainHandler.postDelayed({
                if (!isMutedForOutput && isListeningDesired) {
                    startListening(languageLocale)
                }
            }, 300) // Brief delay to clear residual acoustic reverberation
        } else {
            _microphoneState.value = MicrophoneState.IDLE
        }
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        _microphoneState.value = MicrophoneState.LISTENING
    }

    override fun onBeginningOfSpeech() {
        _microphoneState.value = MicrophoneState.LISTENING
    }

    override fun onRmsChanged(rmsdB: Float) {
        _audioRms.value = rmsdB
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _microphoneState.value = MicrophoneState.PROCESSING
    }

    override fun onError(error: Int) {
        _audioRms.value = 0f

        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Normal timeout in conversational speech loop; auto-restart if continuous listening
                if (isListeningDesired && !isMutedForOutput) {
                    mainHandler.postDelayed({
                        if (isListeningDesired && !isMutedForOutput) {
                            startListening()
                        }
                    }, 400)
                } else {
                    _microphoneState.value = MicrophoneState.IDLE
                }
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                _microphoneState.value = MicrophoneState.ERROR
                onErrorReceived("Boss, I need microphone permission to hear you.")
            }
            SpeechRecognizer.ERROR_AUDIO -> {
                _microphoneState.value = MicrophoneState.ERROR
                onErrorReceived("Boss, I can't access the microphone right now.")
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                mainHandler.postDelayed({
                    if (isListeningDesired && !isMutedForOutput) {
                        try {
                            speechRecognizer?.cancel()
                            startListening()
                        } catch (_: Exception) {}
                    }
                }, 300)
            }
            else -> {
                // Non-fatal error; retry if user still wants listening
                if (isListeningDesired && !isMutedForOutput) {
                    mainHandler.postDelayed({
                        if (isListeningDesired && !isMutedForOutput) {
                            startListening()
                        }
                    }, 500)
                } else {
                    _microphoneState.value = MicrophoneState.IDLE
                }
            }
        }
    }

    override fun onResults(results: Bundle?) {
        _audioRms.value = 0f
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim()

        if (!text.isNullOrEmpty()) {
            _microphoneState.value = MicrophoneState.PROCESSING
            onSpeechRecognized(text)
        } else {
            if (isListeningDesired && !isMutedForOutput) {
                mainHandler.postDelayed({
                    if (isListeningDesired && !isMutedForOutput) {
                        startListening()
                    }
                }, 300)
            } else {
                _microphoneState.value = MicrophoneState.IDLE
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.lowercase() ?: return

        // Interruption / Barge-in detection:
        if (_microphoneState.value == MicrophoneState.SPEAKING || voiceManager.isSpeaking.value) {
            if (text.contains("stop") || text.contains("sana stop") || text.contains("quiet") || text.contains("shut up")) {
                voiceManager.stop()
                onBargeInDetected()
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun cleanup() {
        isListeningDesired = false
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}
        }
        abandonAudioFocus()
    }
}
