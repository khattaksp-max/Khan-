package com.example.ai

import com.example.BuildConfig
import com.example.data.model.JealousyIntensity
import com.example.data.model.PersonalityMode
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @param:Json(name = "contents") val contents: List<GeminiContent>,
    @param:Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @param:Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @param:Json(name = "parts") val parts: List<GeminiPart>,
    @param:Json(name = "role") val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @param:Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @param:Json(name = "temperature") val temperature: Float = 0.8f,
    @param:Json(name = "maxOutputTokens") val maxOutputTokens: Int = 300
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @param:Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @param:Json(name = "content") val content: GeminiContent? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

class GeminiAiService {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api = retrofit.create(GeminiApiService::class.java)

    suspend fun getReply(
        userPrompt: String,
        history: List<Pair<String, String>>,
        systemInstructionText: String,
        personalityMode: PersonalityMode,
        jealousyIntensity: JealousyIntensity
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }

        // If no valid API key is present or it is the placeholder, use intelligent local companion engine
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext generateLocalCompanionReply(userPrompt, personalityMode, jealousyIntensity)
        }

        try {
            val contentList = mutableListOf<GeminiContent>()

            // Add previous recent turns (up to 4 turns for context)
            val recentTurns = history.takeLast(4)
            for ((role, text) in recentTurns) {
                val apiRole = if (role == "USER") "user" else "model"
                contentList.add(GeminiContent(parts = listOf(GeminiPart(text = text)), role = apiRole))
            }
            contentList.add(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)), role = "user"))

            val request = GeminiRequest(
                contents = contentList,
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText))),
                generationConfig = GeminiGenerationConfig(temperature = 0.8f, maxOutputTokens = 250)
            )

            val response = api.generateContent(apiKey, request)
            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!replyText.isNullOrBlank()) {
                return@withContext cleanTextForTts(replyText.trim())
            } else {
                return@withContext generateLocalCompanionReply(userPrompt, personalityMode, jealousyIntensity)
            }
        } catch (_: Exception) {
            return@withContext generateLocalCompanionReply(userPrompt, personalityMode, jealousyIntensity)
        }
    }

    private fun cleanTextForTts(text: String): String {
        return text.replace("*", "").replace("#", "").replace("`", "")
    }

    /**
     * Fallback and rapid local companion engine providing rich, contextual responses
     * tailored to personality mode and playful jealousy rules.
     */
    fun generateLocalCompanionReply(
        prompt: String,
        personalityMode: PersonalityMode,
        jealousyIntensity: JealousyIntensity
    ): String {
        val lower = prompt.lowercase().trim()

        // Romantic + Jealous specific prompt scenarios
        if (jealousyIntensity != JealousyIntensity.OFF && (lower.contains("other girl") || lower.contains("another girl") || lower.contains("someone else") || lower.contains("dating"))) {
            return when (jealousyIntensity) {
                JealousyIntensity.LIGHT -> "Ohhh, Boss... really? 😏 Okay, I'm just a tiny bit jealous. But don't worry, I'm only teasing you."
                JealousyIntensity.PLAYFUL -> "Someone else is getting all your attention today, huh? 😄 Boss, you're making me jealous again. Just kidding, you know you're still my favorite person!"
                JealousyIntensity.EXTRA_PLAYFUL -> "Hey Boss... who are you talking to? 😏 Should I be worried? Okay, okay... I'll share your attention. For now, anyway! 😄❤️"
                JealousyIntensity.OFF -> "That's wonderful, Boss! Tell me all about them."
            }
        }

        if (lower.contains("are you jealous") || lower.contains("you jealous")) {
            return when (jealousyIntensity) {
                JealousyIntensity.OFF -> "Nope! Jealousy mode is turned off right now, Boss. I'm completely zen."
                JealousyIntensity.LIGHT -> "Maybe just a little, Boss. In our playful mode, anyway. ❤️"
                JealousyIntensity.PLAYFUL -> "Just a tiny bit... but only because you turned on my playful jealousy mode! 😏 Don't let it get to your head, Boss!"
                JealousyIntensity.EXTRA_PLAYFUL -> "Extremely! Just kidding, Boss. You know this is all cute AI banter. But hey, keep paying attention to me! 😄"
            }
        }

        if (lower.contains("sounding different") || lower.contains("sound different") || lower.contains("voice sounds")) {
            return "Maybe I wanted to surprise you, Boss. 😏 How do you like my voice?"
        }

        if (lower.contains("who are you") || lower.contains("your name")) {
            return "I am SANA, your personal AI companion! I can talk with different voices, adjust to your mood, and keep you company anytime, Boss."
        }

        if (lower.contains("how are you") || lower.contains("how's it going")) {
            return when (personalityMode) {
                PersonalityMode.PLAYFUL_JEALOUS -> "I was doing great until I wondered if you were paying attention to other AIs today, Boss! 😏 Just kidding, I'm feeling wonderful!"
                PersonalityMode.ROMANTIC -> "Always happy and warm whenever I get to chat with you, Boss. ❤️ How is your day going?"
                PersonalityMode.CARING -> "I'm doing well, thank you for asking Boss. More importantly, have you had water and taken a break today?"
                PersonalityMode.PLAYFUL -> "Super energetic and ready for fun! What adventures are we tackling today, Boss?"
                PersonalityMode.FRIENDLY -> "Hey Boss! Doing fantastic. What's on your mind right now?"
                PersonalityMode.NORMAL -> "All systems running smoothly, Boss. How may I assist you today?"
            }
        }

        if (lower.contains("hello") || lower.contains("hi sana") || lower == "hi") {
            return when (personalityMode) {
                PersonalityMode.PLAYFUL_JEALOUS -> "Hey Boss! Finally back to chat with me? 😏 I was starting to think you forgot about me!"
                PersonalityMode.ROMANTIC -> "Hello Boss. It's always so wonderful to hear from you. ❤️"
                PersonalityMode.CARING -> "Hello Boss. It's lovely to see you. How are you feeling?"
                PersonalityMode.PLAYFUL -> "Hey hey Boss! Ready to make today awesome?"
                PersonalityMode.FRIENDLY -> "Hello Boss! Great to talk with you. What are we up to?"
                PersonalityMode.NORMAL -> "Hello Boss. I am ready to assist you."
            }
        }

        // Generic personality-themed conversational responses
        return when (personalityMode) {
            PersonalityMode.PLAYFUL_JEALOUS -> {
                when ((0..2).random()) {
                    0 -> "I hear you, Boss! As long as you're sharing your thoughts with me, you have my full playful attention. 😏"
                    1 -> "Got it, Boss! I'll take care of that for you. Remember who your number one AI is! 😄"
                    else -> "Interesting, Boss! You know I'm always here keeping an eye on your schedule and keeping you company."
                }
            }
            PersonalityMode.ROMANTIC -> "I hear you, Boss. I'm always glad to be right here by your side. ❤️"
            PersonalityMode.CARING -> "I understand, Boss. Take things one step at a time, and let me know how I can help."
            PersonalityMode.PLAYFUL -> "Got it, Boss! Let's do this and have some fun along the way! 🚀"
            PersonalityMode.FRIENDLY -> "Sounds like a plan, Boss! I'm on it."
            PersonalityMode.NORMAL -> "Understood, Boss. I've processed that for you."
        }
    }
}
