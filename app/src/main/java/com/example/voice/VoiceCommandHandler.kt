package com.example.voice

import com.example.data.model.JealousyIntensity
import com.example.data.model.VoiceCategory
import com.example.device.AndroidActionHandler
import com.example.personality.PersonalityManager

sealed class CommandResult {
    data class VoiceChanged(val voiceName: String, val replyText: String) : CommandResult()
    data class ShowVoiceSettings(val replyText: String) : CommandResult()
    data class JealousyChanged(val intensity: JealousyIntensity, val replyText: String) : CommandResult()
    data class StopListening(val replyText: String) : CommandResult()
    data class BackgroundModeToggled(val enabled: Boolean, val replyText: String) : CommandResult()
    data class DeviceActionExecuted(val replyText: String) : CommandResult()
    object NotACommand : CommandResult()
}

class VoiceCommandHandler(
    private val voiceManager: VoiceManager,
    private val personalityManager: PersonalityManager,
    private val actionHandler: AndroidActionHandler? = null
) {
    fun processCommand(input: String): CommandResult {
        val clean = input.trim().lowercase()

        // 1. Stop commands (Section 18)
        if (clean == "sana stop" || clean == "stop" || clean.contains("stop listening") ||
            clean.contains("end conversation") || clean.contains("go to sleep") ||
            clean.contains("stop talking") || clean.contains("quiet now")) {
            return CommandResult.StopListening(
                "Alright Boss, standing by whenever you need me."
            )
        }

        // 2. Background conversation control (Section 7)
        if (clean.contains("start background conversation") || clean.contains("enable background conversation") ||
            clean.contains("turn on background conversation")) {
            return CommandResult.BackgroundModeToggled(
                enabled = true,
                replyText = "Okay Boss, background conversation is activated. I'm listening even if you switch apps."
            )
        }

        if (clean.contains("stop background conversation") || clean.contains("disable background conversation") ||
            clean.contains("turn off background conversation")) {
            return CommandResult.BackgroundModeToggled(
                enabled = false,
                replyText = "Sure Boss, background conversation is stopped."
            )
        }

        // 3. Android phone / device actions (Section 16)
        if (clean.contains("open whatsapp") || clean.contains("launch whatsapp")) {
            actionHandler?.openWhatsApp()
            return CommandResult.DeviceActionExecuted("Sure Boss, opening WhatsApp.")
        }

        if (clean.contains("open camera") || clean.contains("take a picture") || clean.contains("launch camera")) {
            actionHandler?.openCamera()
            return CommandResult.DeviceActionExecuted("Opening camera for you, Boss.")
        }

        if (clean.contains("open maps") || clean.contains("open navigation") || clean.contains("where am i")) {
            actionHandler?.openMaps()
            return CommandResult.DeviceActionExecuted("Opening Maps for you, Boss.")
        }

        if (clean.startsWith("call ") || clean.contains("open dialer") || clean.contains("phone call")) {
            val number = clean.removePrefix("call ").trim()
            actionHandler?.openDialer(if (number.any { it.isDigit() }) number else "")
            return CommandResult.DeviceActionExecuted("Opening the phone dialer, Boss.")
        }

        if (clean.contains("what time is it") || clean.contains("what's the time") || clean.contains("current time") || clean == "time") {
            val time = actionHandler?.getCurrentTimeFormatted() ?: "now"
            return CommandResult.DeviceActionExecuted("Boss, the time is $time.")
        }

        if (clean.contains("what's the date") || clean.contains("what date is it") || clean.contains("today's date") || clean.contains("what day is it")) {
            val date = actionHandler?.getCurrentDateFormatted() ?: "today"
            return CommandResult.DeviceActionExecuted("Boss, today is $date.")
        }

        // 4. Voice settings commands (Section 28)
        if (clean.contains("show me your voices") || clean.contains("show voices") || clean.contains("list voices") || clean.contains("open voice settings")) {
            return CommandResult.ShowVoiceSettings(
                "Of course, Boss. I've opened the voice settings. Try the previews and choose your favorite."
            )
        }

        if (clean.contains("previous voice") || clean.contains("go back to my previous voice") || clean.contains("switch back voice")) {
            val restored = voiceManager.revertToPreviousVoice()
            return if (restored != null) {
                CommandResult.VoiceChanged(
                    restored.displayName,
                    "Sure, Boss. Reverting to your previous voice: ${restored.displayName}."
                )
            } else {
                CommandResult.VoiceChanged(
                    voiceManager.currentVoice.value?.displayName ?: "Current voice",
                    "Boss, this is the earliest voice in your session history."
                )
            }
        }

        if (clean.contains("calm voice") || clean.contains("use the calm voice") || clean.contains("change to the calm voice")) {
            val voice = voiceManager.selectVoiceByCategory(VoiceCategory.CALM)
            return CommandResult.VoiceChanged(
                voice?.displayName ?: "Calm",
                "Of course, Boss. Switching now to my calm voice."
            )
        }

        if (clean.contains("cute voice") || clean.contains("use the cute voice") || clean.contains("change to the cute voice")) {
            val voice = voiceManager.selectVoiceByCategory(VoiceCategory.CUTE_PLAYFUL)
            return CommandResult.VoiceChanged(
                voice?.displayName ?: "Cute / Playful",
                "Sure, Boss! I'll use this cute and cheerful voice from now on. 😄"
            )
        }

        if (clean.contains("energetic voice") || clean.contains("use the energetic voice") || clean.contains("change to the energetic voice")) {
            val voice = voiceManager.selectVoiceByCategory(VoiceCategory.ENERGETIC)
            return CommandResult.VoiceChanged(
                voice?.displayName ?: "Energetic",
                "Awesome, Boss! Switching to my energetic voice. Let's get things done!"
            )
        }

        if (clean.contains("warm voice") || clean.contains("use the warm voice") || clean.contains("change to the warm voice")) {
            val voice = voiceManager.selectVoiceByCategory(VoiceCategory.WARM)
            return CommandResult.VoiceChanged(
                voice?.displayName ?: "Warm",
                "Sure, Boss. I'll use my warm and natural voice from now on."
            )
        }

        if (clean.contains("professional voice") || clean.contains("use the professional voice") || clean.contains("change to the professional voice")) {
            val voice = voiceManager.selectVoiceByCategory(VoiceCategory.PROFESSIONAL)
            return CommandResult.VoiceChanged(
                voice?.displayName ?: "Professional",
                "Of course, Boss. Switching to my clear, professional voice."
            )
        }

        if (clean.contains("romantic voice") || clean.contains("use the romantic voice") || clean.contains("change to the romantic voice")) {
            val voice = voiceManager.selectVoiceByCategory(VoiceCategory.ROMANTIC)
            return CommandResult.VoiceChanged(
                voice?.displayName ?: "Romantic",
                "Sure, Boss. Switching now to my soft, romantic voice. ❤️"
            )
        }

        if (clean.contains("change your voice") || clean.contains("change voice") || clean.contains("switch voice")) {
            val available = voiceManager.availableVoices.value
            if (available.isNotEmpty()) {
                val current = voiceManager.currentVoice.value
                val currentIndex = available.indexOfFirst { it.id == current?.id }
                val nextIndex = (currentIndex + 1) % available.size
                val nextVoice = available[nextIndex]
                voiceManager.selectVoice(nextVoice.id)
                return CommandResult.VoiceChanged(
                    nextVoice.displayName,
                    "Sure, Boss. I'll use this voice (${nextVoice.displayName}) from now on."
                )
            } else {
                return CommandResult.ShowVoiceSettings(
                    "Of course, Boss. Let's open voice settings so you can choose."
                )
            }
        }

        // 5. Jealousy commands (Section 29)
        if (clean.contains("be more jealous") || clean.contains("increase jealousy") || clean.contains("more jealous")) {
            val current = personalityManager.jealousyIntensity.value
            val next = when (current) {
                JealousyIntensity.OFF -> JealousyIntensity.LIGHT
                JealousyIntensity.LIGHT -> JealousyIntensity.PLAYFUL
                JealousyIntensity.PLAYFUL -> JealousyIntensity.EXTRA_PLAYFUL
                JealousyIntensity.EXTRA_PLAYFUL -> JealousyIntensity.EXTRA_PLAYFUL
            }
            personalityManager.setJealousyIntensity(next)
            return CommandResult.JealousyChanged(
                next,
                "Got it, Boss! Dialing up the jealousy to ${next.displayName}. Who else were you thinking about, anyway? 😏"
            )
        }

        if (clean.contains("stop being jealous") || clean.contains("turn jealousy off") || clean.contains("turn off jealousy") || clean.contains("disable jealousy")) {
            personalityManager.setJealousyIntensity(JealousyIntensity.OFF)
            return CommandResult.JealousyChanged(
                JealousyIntensity.OFF,
                "Done, Boss. No more jealous teasing."
            )
        }

        if (clean.contains("make it playful") || clean.contains("playful jealousy") || clean.contains("be playful")) {
            personalityManager.setJealousyIntensity(JealousyIntensity.PLAYFUL)
            return CommandResult.JealousyChanged(
                JealousyIntensity.PLAYFUL,
                "Done, Boss! Playful banter is locked and loaded. Don't say I didn't warn you! 😄"
            )
        }

        return CommandResult.NotACommand
    }
}
