package com.jarvis.assistant.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.databinding.ActivityApiConnectionBinding
import com.jarvis.assistant.network.GeminiLiveWebSocket
import com.jarvis.assistant.network.ImageGenClient
import com.jarvis.assistant.network.ImageGenResult
import com.jarvis.assistant.util.BrandConfig
import com.jarvis.assistant.util.ExternalLinks
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiConnectionBinding
    private val preferences by lazy { JarvisApp.instance.preferences }
    private val testHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private var testWebSocket: GeminiLiveWebSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityApiConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)

        setupUI()
        loadCurrentPreferences()
        setupListeners()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadCurrentPreferences() {
        binding.etApiKey.setText(preferences.apiKey)
        binding.etCustomEndpoint.setText(preferences.customEndpoint)

        // Gemini Live Model - default to Gemini 3.1 Flash Live Preview
        when (preferences.aiModel) {
            GeminiConstants.MODEL_3_8_LIVE -> binding.rbModelFlash20.isChecked = true
            GeminiConstants.MODEL_2_5_FLASH -> binding.rbModelFlashThinking.isChecked = true
            else -> binding.rbModelFlash15.isChecked = true
        }

        if (preferences.apiKey.isNotBlank()) {
            binding.tvLiveStatus.text = "KEY CONFIGURED • READY"
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22C55E"))
        } else {
            binding.tvLiveStatus.text = "NO API KEY • ENTER KEY BELOW"
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EAB308"))
        }

        if (preferences.customEndpoint.isNotBlank()) {
            binding.tvEndpointSummary.text = "Active Endpoint: ${preferences.customEndpoint}"
        }

        // Image Provider
        when (preferences.imageProvider) {
            "pollinations" -> binding.rbImgPollinations.isChecked = true
            "gemini" -> binding.rbImgGemini.isChecked = true
            "custom" -> binding.rbImgCustom.isChecked = true
            else -> binding.rbImgOpenAi.isChecked = true
        }
        binding.etImageApiKey.setText(preferences.imageApiKey)
        binding.etImageCustomEndpoint.setText(preferences.imageCustomEndpoint)
        updateImageProviderUI(preferences.imageProvider)

        // Code Provider
        when (preferences.codeProvider) {
            "gemini" -> binding.rbCodeGemini.isChecked = true
            "openai" -> binding.rbCodeOpenAI.isChecked = true
            "custom" -> binding.rbCodeCustom.isChecked = true
            else -> binding.rbCodeGroq.isChecked = true
        }
        binding.etCodeApiKey.setText(preferences.codeApiKey)
        binding.etCodeCustomEndpoint.setText(preferences.codeCustomEndpoint)
        binding.etCodeCustomModel.setText(preferences.codeModel)
        when (preferences.codeModel) {
            "deepseek-r1-distill-llama-70b" -> binding.rbCodeModelDeepSeek.isChecked = true
            "gemini-3.1-flash" -> binding.rbCodeModelGemini.isChecked = true
            "gpt-4o-mini" -> binding.rbCodeModelGpt4o.isChecked = true
            else -> binding.rbCodeModelLlama.isChecked = true
        }
        updateCodeProviderUI(preferences.codeProvider)

        // Chat Provider
        when (preferences.chatProvider) {
            "groq" -> binding.rbChatGroq.isChecked = true
            "openai" -> binding.rbChatOpenAI.isChecked = true
            "custom" -> binding.rbChatCustom.isChecked = true
            else -> binding.rbChatGeminiLive.isChecked = true
        }

        // Antigravity MCP Core
        binding.switchAntigravity.isChecked = preferences.antigravityEnabled
        binding.etAntigravityEndpoint.setText(preferences.antigravityEndpoint)
        binding.etAntigravityToken.setText(preferences.antigravityToken)
    }

    private fun updateImageProviderUI(provider: String) {
        when (provider) {
            "pollinations", "gemini" -> {
                binding.layoutImageApiKey.visibility = View.GONE
                binding.layoutImageCustom.visibility = View.GONE
            }
            "custom" -> {
                binding.layoutImageApiKey.visibility = View.VISIBLE
                binding.btnGetImageApiKey.visibility = View.GONE
                binding.layoutImageCustom.visibility = View.VISIBLE
            }
            else -> { // "openai"
                binding.layoutImageApiKey.visibility = View.VISIBLE
                binding.btnGetImageApiKey.visibility = View.VISIBLE
                binding.layoutImageCustom.visibility = View.GONE
            }
        }
    }

    private fun updateCodeProviderUI(provider: String) {
        when (provider) {
            "gemini" -> {
                binding.layoutCodeApiKey.visibility = View.GONE
                binding.layoutCodeCustom.visibility = View.GONE
            }
            "openai" -> {
                binding.layoutCodeApiKey.visibility = View.VISIBLE
                binding.tvCodeKeyTitle.text = "OpenAI API Key"
                binding.btnGetGroqKey.visibility = View.GONE
                binding.btnGetCodeOpenAIKey.visibility = View.VISIBLE
                binding.layoutCodeCustom.visibility = View.GONE
            }
            "custom" -> {
                binding.layoutCodeApiKey.visibility = View.VISIBLE
                binding.tvCodeKeyTitle.text = "Custom Provider API Key"
                binding.btnGetGroqKey.visibility = View.GONE
                binding.btnGetCodeOpenAIKey.visibility = View.GONE
                binding.layoutCodeCustom.visibility = View.VISIBLE
            }
            else -> { // "groq"
                binding.layoutCodeApiKey.visibility = View.VISIBLE
                binding.tvCodeKeyTitle.text = "Groq Cloud API Key"
                binding.btnGetGroqKey.visibility = View.VISIBLE
                binding.btnGetCodeOpenAIKey.visibility = View.GONE
                binding.layoutCodeCustom.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        binding.tvUnifiedFooter.setOnClickListener {
            ExternalLinks.open(this, BrandConfig.TELEGRAM_CHANNEL_URL)
        }

        // Direct Get API Key buttons
        binding.btnGetGeminiKey.setOnClickListener {
            ExternalLinks.open(this, "https://aistudio.google.com/app/apikey")
        }

        binding.btnGetImageApiKey.setOnClickListener {
            ExternalLinks.open(this, "https://platform.openai.com/api-keys")
        }

        binding.btnGetGroqKey.setOnClickListener {
            ExternalLinks.open(this, "https://console.groq.com/keys")
        }

        binding.btnGetCodeOpenAIKey.setOnClickListener {
            ExternalLinks.open(this, "https://platform.openai.com/api-keys")
        }

        // Paste & Clear buttons
        binding.btnPasteKey.setOnClickListener {
            pasteToEditText(binding.etApiKey)
        }
        binding.btnClearKey.setOnClickListener {
            binding.etApiKey.setText("")
        }

        binding.btnPasteImageKey.setOnClickListener {
            pasteToEditText(binding.etImageApiKey)
        }
        binding.btnClearImageKey.setOnClickListener {
            binding.etImageApiKey.setText("")
        }

        binding.btnPasteCodeKey.setOnClickListener {
            pasteToEditText(binding.etCodeApiKey)
        }
        binding.btnClearCodeKey.setOnClickListener {
            binding.etCodeApiKey.setText("")
        }

        // Image Provider RadioGroup
        binding.rgImageProvider.setOnCheckedChangeListener { _, checkedId ->
            val provider = when (checkedId) {
                binding.rbImgPollinations.id -> "pollinations"
                binding.rbImgGemini.id -> "gemini"
                binding.rbImgCustom.id -> "custom"
                else -> "openai"
            }
            updateImageProviderUI(provider)
        }

        // Code Provider RadioGroup
        binding.rgCodeProvider.setOnCheckedChangeListener { _, checkedId ->
            val provider = when (checkedId) {
                binding.rbCodeGemini.id -> "gemini"
                binding.rbCodeOpenAI.id -> "openai"
                binding.rbCodeCustom.id -> "custom"
                else -> "groq"
            }
            updateCodeProviderUI(provider)
        }

        // Test Gemini Latency
        binding.btnTestConnection.setOnClickListener {
            val enteredKey = binding.etApiKey.text?.toString()?.trim() ?: ""
            if (enteredKey.isBlank()) {
                Snackbar.make(binding.root, "Please enter an API key first", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            testConnection(enteredKey)
        }

        // Test Voice Handshake
        binding.btnTestVoiceConnection.setOnClickListener {
            val enteredKey = binding.etApiKey.text?.toString()?.trim() ?: ""
            if (enteredKey.isBlank()) {
                showVoiceResult(false, "FAIL: API key is empty. Enter your key first.")
                return@setOnClickListener
            }
            testVoiceConnection(enteredKey)
        }

        // Test Image Generation
        binding.btnTestImageGeneration.setOnClickListener {
            testImageGeneration()
        }

        // Test Code Generation
        binding.btnTestCodeGeneration.setOnClickListener {
            testCodeGeneration()
        }

        // Test Antigravity MCP Connection
        binding.btnTestAntigravity.setOnClickListener {
            testAntigravityConnection()
        }

        // Save & Apply
        binding.btnSaveConnection.setOnClickListener {
            saveAndApply()
        }
    }

    private fun pasteToEditText(target: com.google.android.material.textfield.TextInputEditText) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = clipboard.primaryClip?.getItemAt(0)
        val pasted = item?.text?.toString()?.trim() ?: ""
        if (pasted.isNotBlank()) {
            target.setText(pasted)
            Toast.makeText(this, "API Key pasted from clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testImageGeneration() {
        val selectedProvider = when (binding.rgImageProvider.checkedRadioButtonId) {
            binding.rbImgPollinations.id -> "pollinations"
            binding.rbImgGemini.id -> "gemini"
            binding.rbImgCustom.id -> "custom"
            else -> "openai"
        }
        val enteredKey = binding.etImageApiKey.text?.toString()?.trim() ?: preferences.imageApiKey
        val geminiKey = binding.etApiKey.text?.toString()?.trim() ?: preferences.apiKey

        binding.btnTestImageGeneration.isEnabled = false
        binding.tvImageTestResult.visibility = View.VISIBLE
        binding.tvImageTestResult.setTextColor(Color.parseColor("#3B82F6"))
        binding.tvImageTestResult.text = "Testing image generation with $selectedProvider..."

        lifecycleScope.launch(Dispatchers.IO) {
            val client = ImageGenClient()
            val result = if (selectedProvider == "openai" || selectedProvider == "custom") {
                if (enteredKey.isBlank()) {
                    withContext(Dispatchers.Main) {
                        binding.btnTestImageGeneration.isEnabled = true
                        binding.tvImageTestResult.setTextColor(Color.parseColor("#EF4444"))
                        binding.tvImageTestResult.text = "FAILED • Please enter an Image API Key first."
                    }
                    return@launch
                }
                client.generateWithOpenAI(
                    context = applicationContext,
                    apiKey = enteredKey,
                    model = "dall-e-3",
                    prompt = "A serene mountain lake at sunrise, highly detailed",
                    aspectRatio = "1:1",
                    customEndpoint = binding.etImageCustomEndpoint.text?.toString()?.trim() ?: ""
                )
            } else {
                client.generateImage(
                    context = applicationContext,
                    apiKey = geminiKey,
                    prompt = "A serene mountain lake at sunrise, highly detailed",
                    preferredProvider = selectedProvider
                )
            }

            withContext(Dispatchers.Main) {
                binding.btnTestImageGeneration.isEnabled = true
                when (result) {
                    is ImageGenResult.Success -> {
                        binding.tvImageTestResult.setTextColor(Color.parseColor("#22C55E"))
                        binding.tvImageTestResult.text = "SUCCESS • Generated via ${result.modelUsed} (${result.imageFile.name})"
                    }
                    is ImageGenResult.Failure -> {
                        binding.tvImageTestResult.setTextColor(Color.parseColor("#EF4444"))
                        binding.tvImageTestResult.text = "FAILED • ${result.reason}"
                    }
                }
            }
        }
    }

    private fun testCodeGeneration() {
        val selectedProvider = when (binding.rgCodeProvider.checkedRadioButtonId) {
            binding.rbCodeGemini.id -> "gemini"
            binding.rbCodeOpenAI.id -> "openai"
            binding.rbCodeCustom.id -> "custom"
            else -> "groq"
        }
        val codeKey = binding.etCodeApiKey.text?.toString()?.trim() ?: preferences.codeApiKey
        val geminiKey = binding.etApiKey.text?.toString()?.trim() ?: preferences.apiKey

        binding.btnTestCodeGeneration.isEnabled = false
        binding.tvCodeTestResult.visibility = View.VISIBLE
        binding.tvCodeTestResult.setTextColor(Color.parseColor("#3B82F6"))
        binding.tvCodeTestResult.text = "Testing code generation with $selectedProvider..."

        lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var isSuccess = false
            var message = ""

            try {
                if (selectedProvider == "groq" || selectedProvider == "openai" || selectedProvider == "custom") {
                    if (codeKey.isBlank()) {
                        withContext(Dispatchers.Main) {
                            binding.btnTestCodeGeneration.isEnabled = true
                            binding.tvCodeTestResult.setTextColor(Color.parseColor("#EF4444"))
                            binding.tvCodeTestResult.text = "FAILED • Please enter a Code API Key first."
                        }
                        return@launch
                    }

                    val endpoint = when (selectedProvider) {
                        "groq" -> "https://api.groq.com/openai/v1/chat/completions"
                        "openai" -> "https://api.openai.com/v1/chat/completions"
                        else -> {
                            val clean = binding.etCodeCustomEndpoint.text?.toString()?.trim()?.removeSuffix("/") ?: ""
                            if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
                        }
                    }
                    val model = when (selectedProvider) {
                        "groq" -> "llama-3.3-70b-versatile"
                        "openai" -> "gpt-4o-mini"
                        else -> binding.etCodeCustomModel.text?.toString()?.trim()?.ifBlank { "llama-3.3-70b-versatile" } ?: "llama-3.3-70b-versatile"
                    }

                    val payload = JSONObject().apply {
                        put("model", model)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", "Write an HTML heading saying Hello World")
                            })
                        })
                        put("max_tokens", 100)
                    }

                    val req = Request.Builder()
                        .url(endpoint)
                        .addHeader("Authorization", "Bearer $codeKey")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val resp = testHttpClient.newCall(req).execute()
                    val latency = System.currentTimeMillis() - startTime
                    if (resp.isSuccessful) {
                        isSuccess = true
                        message = "SUCCESS ($model) • Latency: ${latency}ms • High-speed response"
                    } else {
                        message = "HTTP ${resp.code}: ${resp.body?.string()?.take(150)}"
                    }
                } else {
                    // Gemini test
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=$geminiKey"
                    val payload = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().put("text", "Write an HTML heading saying Hello World")))
                        }))
                    }
                    val req = Request.Builder()
                        .url(url)
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val resp = testHttpClient.newCall(req).execute()
                    val latency = System.currentTimeMillis() - startTime
                    if (resp.isSuccessful) {
                        isSuccess = true
                        message = "SUCCESS (gemini-3.1-flash-lite) • Latency: ${latency}ms"
                    } else {
                        message = "HTTP ${resp.code}: ${resp.body?.string()?.take(150)}"
                    }
                }
            } catch (e: Exception) {
                message = "Error: ${e.message}"
            }

            withContext(Dispatchers.Main) {
                binding.btnTestCodeGeneration.isEnabled = true
                if (isSuccess) {
                    binding.tvCodeTestResult.setTextColor(Color.parseColor("#22C55E"))
                    binding.tvCodeTestResult.text = message
                } else {
                    binding.tvCodeTestResult.setTextColor(Color.parseColor("#EF4444"))
                    binding.tvCodeTestResult.text = "FAILED • $message"
                }
            }
        }
    }

    private fun testVoiceConnection(apiKey: String) {
        val selectedModel = when (binding.rgModelGroup.checkedRadioButtonId) {
            binding.rbModelFlash20.id -> GeminiConstants.MODEL_3_8_LIVE
            binding.rbModelFlashThinking.id -> GeminiConstants.MODEL_2_5_FLASH
            else -> GeminiConstants.MODEL_3_1_FLASH
        }
        val voice = preferences.voice

        binding.btnTestVoiceConnection.isEnabled = false
        binding.tvVoiceTestResult.visibility = View.VISIBLE
        binding.tvVoiceTestResult.setTextColor(Color.parseColor("#3B82F6"))
        binding.tvVoiceTestResult.text = "Stage 1/5 - Testing microphone audio capture..."

        lifecycleScope.launch {
            val micPass = withContext(Dispatchers.IO) {
                var recorder: AudioRecord? = null
                try {
                    val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    recorder = AudioRecord(
                        android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf.coerceAtLeast(3200)
                    )
                    var maxAmp = 0f
                    var totalBytes = 0
                    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                        return@withContext Pair(false, "AudioRecord failed to initialize.")
                    }
                    recorder.startRecording()
                    val buf = ByteArray(640)
                    val startRec = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startRec < 1000L) {
                        val read = recorder.read(buf, 0, buf.size)
                        if (read > 0) {
                            totalBytes += read
                            val freq = com.jarvis.assistant.audio.AudioUtils.analyzeFrequency(buf, read, 16000, 1.0f)
                            if (freq.amplitude > maxAmp) maxAmp = freq.amplitude
                        }
                    }
                    recorder.stop()
                    recorder.release()
                    if (totalBytes == 0 || maxAmp < 0.0001f) {
                        Pair(false, "Mic captured only zero RMS / silence.")
                    } else {
                        Pair(true, "Captured sound successfully ($totalBytes bytes).")
                    }
                } catch (e: Exception) {
                    try { recorder?.release() } catch (_: Exception) {}
                    Pair(false, "Mic capture exception: ${e.message}")
                }
            }

            if (!micPass.first) {
                binding.btnTestVoiceConnection.isEnabled = true
                showVoiceResult(false, "FAIL (Stage 1: Mic Capture): ${micPass.second}")
                return@launch
            }

            binding.tvVoiceTestResult.text = "Stage 1 PASS. Stage 2/5 - Connecting to Gemini Live WebSocket..."
            val audioBytesCollected = mutableListOf<ByteArray>()
            var stage = "Stage 2 (Handshake)"
            var completed = false
            var failureReason: String? = null

            val testListener = object : GeminiLiveWebSocket.Listener {
                override fun onConnectionStateChanged(status: String) {
                    if (completed) return
                    binding.tvVoiceTestResult.text = "Testing voice: $status"
                    if (status == "LIVE") {
                        stage = "Stage 3 (Greeting Turn)"
                        binding.tvVoiceTestResult.text = "Stage 2 PASS. Stage 3/5 - Sending greeting turn..."
                        testWebSocket?.sendClientContent("Say hello briefly in one sentence")
                    }
                }

                override fun onAudioDataReceived(pcmData: ByteArray) {
                    if (completed) return
                    audioBytesCollected.add(pcmData)
                    stage = "Stage 4 (Receiving Audio)"
                    binding.tvVoiceTestResult.text = "Stage 4/5 - Receiving audio (${audioBytesCollected.sumOf { it.size }} bytes)..."
                }

                override fun onAssistantTextReceived(textChunk: String) {}
                override fun onUserTextReceived(textChunk: String) {}
                override fun onInterrupted() {}
                override fun onTurnCompleted() {
                    stage = "Stage 4 (Turn Complete)"
                }
                override fun onError(message: String, isFatal: Boolean, errorCode: String?) {
                    if (completed) return
                    failureReason = message
                }
                override fun onToolCall(callId: String, name: String, args: JSONObject) {}
            }

            val testWs = GeminiLiveWebSocket(
                apiKey = apiKey,
                model = selectedModel,
                voiceName = voice,
                systemPrompt = "You are Siya Ai. Reply in one short friendly sentence.",
                listener = testListener
            )
            testWebSocket = testWs
            testWs.connect(lifecycleScope)

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 12000L) {
                delay(200)
                if (failureReason != null) break
                if (audioBytesCollected.isNotEmpty() && (stage == "Stage 4 (Turn Complete)" || System.currentTimeMillis() - startTime > 6000L)) {
                    break
                }
            }

            completed = true
            testWs.disconnect()
            testWebSocket = null
            binding.btnTestVoiceConnection.isEnabled = true

            if (failureReason != null) {
                showVoiceResult(false, "FAIL ($stage): $failureReason")
                return@launch
            }

            val totalBytes = audioBytesCollected.sumOf { it.size }
            if (totalBytes == 0) {
                showVoiceResult(false, "FAIL (Stage 4: Audio Received): No audio chunks returned by Gemini Live.")
                return@launch
            }

            binding.tvVoiceTestResult.text = "Stage 4 PASS (Received $totalBytes bytes). Stage 5/5 - Testing AudioTrack playback..."

            var playedSuccessfully = false
            withContext(Dispatchers.IO) {
                try {
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(24000)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build()
                        )
                        .setBufferSizeInBytes(totalBytes.coerceAtLeast(48000))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    track.play()
                    for (chunk in audioBytesCollected) {
                        track.write(chunk, 0, chunk.size)
                    }
                    delay(300)
                    track.stop()
                    track.release()
                    playedSuccessfully = true
                } catch (e: Exception) {
                    playedSuccessfully = false
                }
            }

            if (playedSuccessfully) {
                showVoiceResult(true, "PASS (5/5 Stages): Neural speech played aloud ($voice) successfully!")
            } else {
                showVoiceResult(false, "FAIL (Stage 5: Playback): AudioTrack failed to play.")
            }
        }
    }

    private fun showVoiceResult(success: Boolean, message: String) {
        binding.tvVoiceTestResult.visibility = View.VISIBLE
        if (success) {
            binding.tvVoiceTestResult.setTextColor(Color.parseColor("#22C55E"))
            binding.tvVoiceTestResult.text = message
        } else {
            binding.tvVoiceTestResult.setTextColor(Color.parseColor("#EF4444"))
            binding.tvVoiceTestResult.text = message
        }
    }

    private fun testConnection(apiKey: String) {
        binding.btnTestConnection.isEnabled = false
        binding.tvPingResult.text = "Testing ping..."
        binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3B82F6"))

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                    val request = Request.Builder().url(url).build()
                    testHttpClient.newCall(request).execute().use { response ->
                        val duration = System.currentTimeMillis() - startTime
                        if (response.isSuccessful) {
                            Pair(true, duration)
                        } else {
                            Pair(false, response.code.toLong())
                        }
                    }
                } catch (e: Exception) {
                    Pair(false, -1L)
                }
            }

            binding.btnTestConnection.isEnabled = true
            if (result.first) {
                val ping = result.second
                binding.tvPingResult.text = "Ping: ${ping}ms • Valid"
                binding.tvLiveStatus.text = "CONNECTED • HANDSHAKE OK"
                binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22C55E"))
                Snackbar.make(binding.root, "Connection verified! Ping: ${ping}ms", Snackbar.LENGTH_LONG).show()
            } else {
                val code = result.second
                binding.tvPingResult.text = if (code == -1L) "Network Timeout" else "HTTP $code Error"
                binding.tvLiveStatus.text = "FAILED • INVALID KEY OR NETWORK"
                binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                Snackbar.make(binding.root, "Connection failed. Please verify API key.", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun saveAndApply() {
        val key = binding.etApiKey.text?.toString()?.trim() ?: ""
        val customEp = binding.etCustomEndpoint.text?.toString()?.trim() ?: ""

        val selectedModel = when (binding.rgModelGroup.checkedRadioButtonId) {
            binding.rbModelFlash20.id -> GeminiConstants.MODEL_3_8_LIVE
            binding.rbModelFlashThinking.id -> GeminiConstants.MODEL_2_5_FLASH
            else -> GeminiConstants.MODEL_3_1_FLASH
        }

        val imageProvider = when (binding.rgImageProvider.checkedRadioButtonId) {
            binding.rbImgPollinations.id -> "pollinations"
            binding.rbImgGemini.id -> "gemini"
            binding.rbImgCustom.id -> "custom"
            else -> "openai"
        }

        val codeProvider = when (binding.rgCodeProvider.checkedRadioButtonId) {
            binding.rbCodeGemini.id -> "gemini"
            binding.rbCodeOpenAI.id -> "openai"
            binding.rbCodeCustom.id -> "custom"
            else -> "groq"
        }

        val codeModel = when {
            codeProvider == "custom" -> binding.etCodeCustomModel.text?.toString()?.trim()?.ifBlank { "llama-3.3-70b-versatile" } ?: "llama-3.3-70b-versatile"
            binding.rbCodeModelDeepSeek.isChecked -> "deepseek-r1-distill-llama-70b"
            binding.rbCodeModelGemini.isChecked -> "gemini-3.1-flash"
            binding.rbCodeModelGpt4o.isChecked -> "gpt-4o-mini"
            else -> "llama-3.3-70b-versatile"
        }

        val chatProvider = when (binding.rgChatProvider.checkedRadioButtonId) {
            binding.rbChatGroq.id -> "groq"
            binding.rbChatOpenAI.id -> "openai"
            binding.rbChatCustom.id -> "custom"
            else -> "gemini_live"
        }

        preferences.apiKey = key
        preferences.aiModel = selectedModel
        preferences.customEndpoint = customEp

        preferences.imageProvider = imageProvider
        preferences.imageApiKey = binding.etImageApiKey.text?.toString()?.trim() ?: ""
        preferences.imageCustomEndpoint = binding.etImageCustomEndpoint.text?.toString()?.trim() ?: ""

        preferences.codeProvider = codeProvider
        preferences.codeApiKey = binding.etCodeApiKey.text?.toString()?.trim() ?: ""
        preferences.codeModel = codeModel
        preferences.codeCustomEndpoint = binding.etCodeCustomEndpoint.text?.toString()?.trim() ?: ""

        preferences.chatProvider = chatProvider

        preferences.antigravityEnabled = binding.switchAntigravity.isChecked
        preferences.antigravityEndpoint = binding.etAntigravityEndpoint.text?.toString()?.trim()
            ?.ifBlank { com.jarvis.assistant.network.McpClient.DEFAULT_ENDPOINT }
            ?: com.jarvis.assistant.network.McpClient.DEFAULT_ENDPOINT
        preferences.antigravityToken = binding.etAntigravityToken.text?.toString()?.trim() ?: ""

        com.jarvis.assistant.background.JarvisConversationService.clearLiveError()
        com.jarvis.assistant.background.JarvisConversationService.restartSession()

        Toast.makeText(this, "API, Antigravity & Provider settings saved!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testAntigravityConnection() {
        val endpoint = binding.etAntigravityEndpoint.text?.toString()?.trim()
            ?.ifBlank { com.jarvis.assistant.network.McpClient.DEFAULT_ENDPOINT }
            ?: com.jarvis.assistant.network.McpClient.DEFAULT_ENDPOINT
        val token = binding.etAntigravityToken.text?.toString()?.trim()

        binding.btnTestAntigravity.isEnabled = false
        binding.tvAntigravityStatus.setTextColor(Color.parseColor("#3B82F6"))
        binding.tvAntigravityStatus.text = "Pinging MCP endpoint..."

        lifecycleScope.launch(Dispatchers.IO) {
            val client = com.jarvis.assistant.network.McpClient(endpoint, token)
            val isHealthy = client.checkHealth()
            val tools = if (isHealthy) client.listTools() else emptyList()

            withContext(Dispatchers.Main) {
                binding.btnTestAntigravity.isEnabled = true
                if (isHealthy) {
                    binding.tvAntigravityStatus.setTextColor(Color.parseColor("#22C55E"))
                    binding.tvAntigravityStatus.text = "ONLINE • ${tools.size} tools discovered"
                } else {
                    binding.tvAntigravityStatus.setTextColor(Color.parseColor("#EAB308"))
                    binding.tvAntigravityStatus.text = "OFFLINE • Local server standby"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        testWebSocket?.disconnect()
        testWebSocket = null
    }
}
