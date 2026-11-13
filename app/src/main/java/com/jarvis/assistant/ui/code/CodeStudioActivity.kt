package com.jarvis.assistant.ui.code

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.data.model.GeminiConstants
import com.jarvis.assistant.databinding.ActivityCodeStudioBinding
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CodeStudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCodeStudioBinding
    private val preferences by lazy { com.jarvis.assistant.JarvisApp.instance.preferences }
    private var isEditModeActive = false
    private var currentProjectCode = ""
    private var currentProjectTitle = "Interactive App"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "siya_code_studio"
        private const val NOTIFICATION_ID = 2026
        private const val DEFAULT_STARTER_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Siya Live Studio</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #FFF0EA 0%, #FED7AA 100%);
            color: #1E293B;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .card {
            background: rgba(255, 255, 255, 0.88);
            border: 1px solid rgba(251, 146, 60, 0.4);
            border-radius: 24px;
            padding: 32px;
            max-width: 420px;
            width: 100%;
            text-align: center;
            box-shadow: 0 20px 40px rgba(251, 146, 60, 0.18);
            backdrop-filter: blur(16px);
        }
        h1 { font-size: 24px; color: #EA580C; margin-bottom: 8px; font-weight: 700; }
        p { color: #64748B; font-size: 14px; line-height: 1.6; margin-bottom: 24px; }
        .counter-box {
            background: #FFF7ED;
            border: 2px dashed #FDBA74;
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 24px;
            font-size: 38px;
            font-weight: 800;
            color: #EA580C;
        }
        .btn-group { display: flex; gap: 12px; justify-content: center; }
        button {
            border: none;
            background: #EA580C;
            color: white;
            font-size: 16px;
            font-weight: 600;
            padding: 12px 24px;
            border-radius: 12px;
            cursor: pointer;
            transition: all 0.2s ease;
            box-shadow: 0 4px 12px rgba(234, 88, 12, 0.25);
        }
        button:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(234, 88, 12, 0.35); }
        button:active { transform: translateY(0); }
        button.secondary { background: #FED7AA; color: #9A3412; box-shadow: none; }
    </style>
</head>
<body>
    <div class="card">
        <h1>Siya Live Studio</h1>
        <p>Interactive web application compiled &amp; deployed live on device.</p>
        <div class="counter-box" id="valCounter">0</div>
        <div class="btn-group">
            <button class="secondary" onclick="updateVal(-1)">- Decrease</button>
            <button onclick="updateVal(1)">+ Increase</button>
        </div>
    </div>
    <script>
        let count = 0;
        function updateVal(d) {
            count += d;
            document.getElementById('valCounter').innerText = count;
        }
    </script>
</body>
</html>"""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCodeStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createNotificationChannel()
        setupWebView()
        setupTabs()
        setupActions()
        setupSuggestionChips()
        setupAiRecommendationClicks()

        // Load default starter project
        loadCodeIntoStudio(DEFAULT_STARTER_HTML, "Interactive App")
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Siya Code Studio"
            val descriptionText = "Notifications when new projects are generated and deployed live"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupWebView() {
        binding.wvLivePreview.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.tvLiveUrl.text = url ?: "https://siya-app.local/live-preview"
                }
            }
        }
    }

    private fun setupTabs() {
        binding.tabLivePreview.setOnClickListener { switchToLivePreview() }
        binding.tabCodeEditor.setOnClickListener { switchToCodeEditor() }
        binding.tabAiInsights.setOnClickListener { switchToAiInsights() }
    }

    private fun switchToLivePreview() {
        val editorContent = binding.etCodeEditor.text.toString()
        if (editorContent != currentProjectCode && editorContent.isNotBlank()) {
            currentProjectCode = editorContent
            deployToLiveWebView(currentProjectCode)
        }

        binding.viewGeneratingCodeBox.visibility = View.GONE
        binding.viewLiveDeployment.visibility = View.VISIBLE
        binding.viewCodeEditor.visibility = View.GONE
        binding.viewAiInsights.visibility = View.GONE

        val accentColor = getThemeColor(R.attr.themeAccent)
        val mutedColor = getThemeColor(R.attr.themeTextMuted)

        binding.tabLivePreview.setTextColor(accentColor)
        binding.tabLivePreview.setBackgroundResource(R.drawable.bg_tab_selected_bottom)
        binding.tabCodeEditor.setTextColor(mutedColor)
        binding.tabCodeEditor.background = null
        binding.tabAiInsights.setTextColor(mutedColor)
        binding.tabAiInsights.background = null
    }

    private fun switchToCodeEditor() {
        binding.viewGeneratingCodeBox.visibility = View.GONE
        binding.viewLiveDeployment.visibility = View.GONE
        binding.viewCodeEditor.visibility = View.VISIBLE
        binding.viewAiInsights.visibility = View.GONE

        val accentColor = getThemeColor(R.attr.themeAccent)
        val mutedColor = getThemeColor(R.attr.themeTextMuted)

        binding.tabCodeEditor.setTextColor(accentColor)
        binding.tabCodeEditor.setBackgroundResource(R.drawable.bg_tab_selected_bottom)
        binding.tabLivePreview.setTextColor(mutedColor)
        binding.tabLivePreview.background = null
        binding.tabAiInsights.setTextColor(mutedColor)
        binding.tabAiInsights.background = null
    }

    private fun switchToAiInsights() {
        binding.viewGeneratingCodeBox.visibility = View.GONE
        binding.viewLiveDeployment.visibility = View.GONE
        binding.viewCodeEditor.visibility = View.GONE
        binding.viewAiInsights.visibility = View.VISIBLE

        val accentColor = getThemeColor(R.attr.themeAccent)
        val mutedColor = getThemeColor(R.attr.themeTextMuted)

        binding.tabAiInsights.setTextColor(accentColor)
        binding.tabAiInsights.setBackgroundResource(R.drawable.bg_tab_selected_bottom)
        binding.tabLivePreview.setTextColor(mutedColor)
        binding.tabLivePreview.background = null
        binding.tabCodeEditor.setTextColor(mutedColor)
        binding.tabCodeEditor.background = null
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // New Project Button
        binding.btnNewProject.setOnClickListener {
            startNewProject()
        }

        // Export APK Button
        binding.btnExportApk.setOnClickListener {
            exportProjectAsApk()
        }

        // Toggle Direct Editing
        binding.btnToggleEdit.setOnClickListener {
            isEditModeActive = !isEditModeActive
            if (isEditModeActive) {
                binding.btnToggleEdit.text = "Done"
                binding.etCodeEditor.isFocusable = true
                binding.etCodeEditor.isFocusableInTouchMode = true
                binding.etCodeEditor.requestFocus()
                switchToCodeEditor()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.etCodeEditor, InputMethodManager.SHOW_IMPLICIT)
                Toast.makeText(this, "Code editing enabled. Tap Run to update preview.", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnToggleEdit.text = "Edit"
                binding.etCodeEditor.isFocusable = false
                binding.etCodeEditor.isFocusableInTouchMode = false
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etCodeEditor.windowToken, 0)

                // Sync changes into live deployment view
                currentProjectCode = binding.etCodeEditor.text.toString()
                deployToLiveWebView(currentProjectCode)
                Toast.makeText(this, "Changes saved & live deployment refreshed!", Toast.LENGTH_SHORT).show()
            }
        }

        // Run Button in Editor
        binding.btnRunCode.setOnClickListener {
            currentProjectCode = binding.etCodeEditor.text.toString()
            deployToLiveWebView(currentProjectCode)
            switchToLivePreview()
            Toast.makeText(this, "Deployed updated code to Live Preview!", Toast.LENGTH_SHORT).show()
        }

        // Refresh Preview Button in Address bar
        binding.btnRefreshPreview.setOnClickListener {
            deployToLiveWebView(currentProjectCode)
            Toast.makeText(this, "Live preview refreshed", Toast.LENGTH_SHORT).show()
        }

        // Download ZIP Button
        binding.btnDownloadZip.setOnClickListener {
            downloadProjectZip()
        }

        // Generate Code Button
        binding.btnGenerateCode.setOnClickListener {
            val prompt = binding.etCodePrompt.text?.toString()?.trim() ?: ""
            if (prompt.isNotBlank()) {
                generateCodeWithAI(prompt)
            } else {
                Toast.makeText(this, "Please describe the code or website you want to build.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startNewProject() {
        currentProjectCode = DEFAULT_STARTER_HTML
        currentProjectTitle = "New Project"
        loadCodeIntoStudio(DEFAULT_STARTER_HTML, "New Project")
        binding.etCodePrompt.setText("")
        Toast.makeText(this, "Created new project canvas.", Toast.LENGTH_SHORT).show()
    }

    private fun setupSuggestionChips() {
        binding.chipPortfolio.setOnClickListener {
            binding.etCodePrompt.setText("Build a personal portfolio website with interactive skill bars, project cards, and contact form")
            generateCodeWithAI(binding.etCodePrompt.text.toString())
        }

        binding.chipGame.setOnClickListener {
            binding.etCodePrompt.setText("Create a responsive Canvas Arcade Game with smooth touch controls, particle effects, and score tracking")
            generateCodeWithAI(binding.etCodePrompt.text.toString())
        }

        binding.chipPython.setOnClickListener {
            binding.etCodePrompt.setText("Create a Python script for mathematical analysis and data simulation with an interactive console UI")
            generateCodeWithAI(binding.etCodePrompt.text.toString())
        }

        binding.chipCalculator.setOnClickListener {
            binding.etCodePrompt.setText("Build a modern scientific calculator with history tape, smooth button animations, and light/dark theme")
            generateCodeWithAI(binding.etCodePrompt.text.toString())
        }

        binding.chipDashboard.setOnClickListener {
            binding.etCodePrompt.setText("Create a modern analytics dashboard with animated charts, metrics cards, and activity log")
            generateCodeWithAI(binding.etCodePrompt.text.toString())
        }

        binding.chipKanban.setOnClickListener {
            binding.etCodePrompt.setText("Build a clean drag-and-drop Task Kanban Board with To Do, In Progress, Done columns and local storage persistence")
            generateCodeWithAI(binding.etCodePrompt.text.toString())
        }
    }

    private fun setupAiRecommendationClicks() {
        binding.chipRecLocalStorage.setOnClickListener {
            upgradeCurrentProjectWithFeature("Add full localStorage persistence so data, counters, or user state are automatically saved and reloaded upon refresh")
        }

        binding.chipRecAnimations.setOnClickListener {
            upgradeCurrentProjectWithFeature("Add smooth micro-animations, button hover/tap scale effects, and Web Audio API synthesized sound effects on interactions")
        }

        binding.chipRecDarkTheme.setOnClickListener {
            upgradeCurrentProjectWithFeature("Add an interactive Light / Dark Mode theme toggle switch with smooth CSS variable transitions")
        }

        binding.chipRecExportJson.setOnClickListener {
            upgradeCurrentProjectWithFeature("Add a one-click Export Data button to download the current state as a JSON or text file")
        }

        binding.btnAiAutoUpgrade.setOnClickListener {
            upgradeCurrentProjectWithFeature("Polish the design to ultra-modern UI standards, improve responsive typography, add subtle glassmorphism cards, and optimize mobile touch interactions")
        }
    }

    private fun upgradeCurrentProjectWithFeature(upgradePrompt: String) {
        val currentCode = binding.etCodeEditor.text.toString().ifBlank { currentProjectCode }
        if (currentCode.isBlank()) {
            Toast.makeText(this, "No active code to upgrade. Please generate a project first.", Toast.LENGTH_SHORT).show()
            return
        }

        val prompt = "UPGRADE THIS EXISTING APPLICATION:\n\n$upgradePrompt\n\nExisting Source Code:\n```html\n$currentCode\n```\n\nRewrite the full standalone application incorporating this upgrade cleanly."
        generateCodeWithAI(prompt, isUpgrade = true)
    }

    private fun generateCodeWithAI(userPrompt: String, isUpgrade: Boolean = false) {
        val prefs = (application as JarvisApp).preferences
        val apiKey = prefs.apiKey.ifBlank { GeminiConstants.RELEASE_API_KEY }.trim()

        if (apiKey.isBlank()) {
            Toast.makeText(this, "Please set your Gemini API key in Settings.", Toast.LENGTH_LONG).show()
            return
        }

        val currentCode = binding.etCodeEditor.text.toString().ifBlank { currentProjectCode }
        val isUpdateIntent = isUpgrade || (currentCode.isNotBlank() && listOf("add", "update", "change", "fix", "improve", "make it")
            .any { userPrompt.contains(it, ignoreCase = true) })

        // 1. Show Live Code Generating Stream Box
        binding.viewGeneratingCodeBox.visibility = View.VISIBLE
        binding.viewLiveDeployment.visibility = View.GONE
        binding.viewCodeEditor.visibility = View.GONE
        binding.viewAiInsights.visibility = View.GONE

        binding.tvGeneratingTitle.text = if (isUpdateIntent) "Siya AI updating code..." else "Siya AI coding project..."
        binding.tvGeneratingCodeStream.text = "// Initializing architecture for: ${userPrompt.take(40)}...\n// Crafting responsive HTML5, modern CSS, and JavaScript..."
        binding.tvGeneratingCharCount.text = "0 chars"

        binding.pbGenerating.visibility = View.VISIBLE
        binding.btnGenerateCode.isEnabled = false
        binding.etCodePrompt.isEnabled = false

        // Simulate live code generation typing in the box while network executes
        val streamJob = lifecycleScope.launch(Dispatchers.Main) {
            val starterLines = listOf(
                "<!DOCTYPE html>",
                "<html lang=\"en\">",
                "<head>",
                "  <meta charset=\"UTF-8\">",
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">",
                "  <title>Siya Generated Project</title>",
                "  <style>",
                "    :root { --primary: #EA580C; --bg: #0F172A; }",
                "    body { font-family: system-ui, sans-serif; margin: 0; padding: 16px; }",
                "    .container { max-width: 600px; margin: auto; }",
                "  </style>",
                "</head>",
                "<body>",
                "  <script>",
                "    console.log('Siya App booting up...');",
                "  </script>"
            )
            val sb = StringBuilder()
            for (line in starterLines) {
                if (!binding.pbGenerating.isShown) break
                sb.append(line).append("\n")
                binding.tvGeneratingCodeStream.text = sb.toString()
                binding.tvGeneratingCharCount.text = "${sb.length} chars"
                binding.svGeneratingCode.fullScroll(View.FOCUS_DOWN)
                delay(120)
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val systemPrompt = "You are Siya AI Pro's Elite Full-Stack Code Architect. " +
                        "Generate a complete, fully functional, self-contained single-file HTML/CSS/JavaScript web application or tool based on the user's request. " +
                        "If the user asks for Python code, provide a standalone Python terminal runner web page that renders the Python code and simulates its terminal execution. " +
                        "Requirements: " +
                        "1. Include all HTML, modern responsive CSS in <style> tags, and working interactive JavaScript in <script> tags. " +
                        "2. Design must be ultra-modern, beautiful, responsive, and mobile-friendly with viewport meta. " +
                        "3. Do NOT include markdown explanations outside the code block. Output ONLY the complete standalone HTML document inside a ```html ... ``` block."

                val finalUserPrompt = if (isUpdateIntent && currentCode.isNotBlank()) {
                    "UPDATE THIS EXISTING WEB APPLICATION:\n\nUser Change Request:\n$userPrompt\n\nExisting Source Code:\n```html\n$currentCode\n```\n\nPreserve working existing functionality and output the complete updated standalone HTML document."
                } else {
                    "User Coding Request: $userPrompt"
                }

                val codeProvider = preferences.codeProvider
                val codeKey = preferences.codeApiKey.trim()
                var textOutput = ""
                var successfulModel = ""
                var lastErrorMessage = ""

                // 0. Antigravity MCP Agent Engine
                if (preferences.antigravityEnabled || codeProvider.equals("antigravity", ignoreCase = true)) {
                    try {
                        val mcp = com.jarvis.assistant.network.McpClient(
                            preferences.antigravityEndpoint,
                            preferences.antigravityToken.takeIf { it.isNotBlank() }
                        )
                        val mcpRes = mcp.generateWithAntigravity(finalUserPrompt, "code")
                        if (mcpRes.success && mcpRes.output.isNotBlank()) {
                            textOutput = mcpRes.output
                            successfulModel = "Google Antigravity MCP Core"
                        }
                    } catch (e: Exception) {
                        Log.w("CodeStudio", "Antigravity MCP error: ${e.message}")
                    }
                }

                // 1. Try Groq or OpenAI or Custom if selected and configured
                if (textOutput.isBlank() && (codeProvider == "groq" || codeProvider == "openai" || codeProvider == "custom") && codeKey.isNotBlank()) {
                    try {
                        val endpoint = when (codeProvider) {
                            "groq" -> "https://api.groq.com/openai/v1/chat/completions"
                            "openai" -> "https://api.openai.com/v1/chat/completions"
                            else -> {
                                val clean = preferences.codeCustomEndpoint.trim().removeSuffix("/")
                                if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
                            }
                        }
                        val model = when (codeProvider) {
                            "groq" -> preferences.codeModel.ifBlank { "llama-3.3-70b-versatile" }
                            "openai" -> preferences.codeModel.ifBlank { "gpt-4o-mini" }
                            else -> preferences.codeModel.ifBlank { "llama-3.3-70b-versatile" }
                        }

                        val chatPayload = JSONObject().apply {
                            put("model", model)
                            put("messages", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "system")
                                    put("content", systemPrompt)
                                })
                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("content", finalUserPrompt)
                                })
                            })
                            put("temperature", 0.4)
                            put("max_tokens", 8192)
                        }

                        val request = Request.Builder()
                            .url(endpoint)
                            .addHeader("Authorization", "Bearer $codeKey")
                            .addHeader("Content-Type", "application/json")
                            .post(chatPayload.toString().toRequestBody("application/json".toMediaType()))
                            .build()

                        val response = httpClient.newCall(request).execute()
                        val body = response.body?.string() ?: ""
                        if (response.isSuccessful && body.isNotBlank()) {
                            val jsonResp = JSONObject(body)
                            val choices = jsonResp.optJSONArray("choices")
                            val firstChoice = choices?.optJSONObject(0)
                            val messageObj = firstChoice?.optJSONObject("message")
                            textOutput = messageObj?.optString("content", "") ?: ""
                            successfulModel = "$codeProvider ($model)"
                        } else {
                            lastErrorMessage = "$codeProvider HTTP ${response.code}: $body"
                        }
                    } catch (e: Exception) {
                        lastErrorMessage = "$codeProvider error: ${e.message}"
                    }
                }

                // 2. Fallback to Gemini if textOutput is blank
                if (textOutput.isBlank() && apiKey.isNotBlank()) {
                    val payload = JSONObject().apply {
                        put("contents", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", "$systemPrompt\n\n$finalUserPrompt"))
                            })
                        }))
                        put("generationConfig", JSONObject().apply {
                            put("temperature", 0.4)
                            put("maxOutputTokens", 8192)
                        })
                    }

                    val candidateModels = listOf(
                        "gemini-3.1-flash-lite",
                        "gemini-3.5-flash",
                        "gemini-flash-lite-latest",
                        "gemini-4-hydrogen",
                        "gemini-3-flash-preview"
                    )

                    for (modelName in candidateModels) {
                        try {
                            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                            val request = Request.Builder()
                                .url(url)
                                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                                .build()

                            val response = httpClient.newCall(request).execute()
                            val body = response.body?.string() ?: ""

                            if (response.isSuccessful && body.isNotBlank()) {
                                val jsonResponse = JSONObject(body)
                                val candidates = jsonResponse.optJSONArray("candidates")
                                val firstCandidate = candidates?.optJSONObject(0)
                                val content = firstCandidate?.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                textOutput = parts?.optJSONObject(0)?.optString("text", "") ?: ""
                                successfulModel = "Gemini ($modelName)"
                                break
                            } else {
                                lastErrorMessage = "Model $modelName HTTP ${response.code}: $body"
                            }
                        } catch (e: Exception) {
                            lastErrorMessage = "Model $modelName error: ${e.message}"
                        }
                    }
                }

                streamJob.cancel()

                if (textOutput.isBlank()) {
                    throw Exception(lastErrorMessage.ifBlank { "All code generation providers were unavailable. Please check your API key in Settings." })
                }

                val extractedCode = extractHtmlFromResponse(textOutput)

                withContext(Dispatchers.Main) {
                    binding.pbGenerating.visibility = View.GONE
                    binding.btnGenerateCode.isEnabled = true
                    binding.etCodePrompt.isEnabled = true

                    if (extractedCode.isNotBlank()) {
                        val title = if (isUpdateIntent) "$currentProjectTitle (Updated)" else userPrompt.take(30)
                        currentProjectTitle = title
                        loadCodeIntoStudio(extractedCode, currentProjectTitle)
                        sendGenerationNotification(currentProjectTitle)
                        val successMsg = if (isUpdateIntent) "Project updated & deployed live!" else "Code generated via $successfulModel & deployed live!"
                        Toast.makeText(this@CodeStudioActivity, successMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        binding.viewGeneratingCodeBox.visibility = View.GONE
                        switchToLivePreview()
                        Toast.makeText(this@CodeStudioActivity, "Could not extract HTML code. Please retry.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                streamJob.cancel()
                withContext(Dispatchers.Main) {
                    binding.pbGenerating.visibility = View.GONE
                    binding.btnGenerateCode.isEnabled = true
                    binding.etCodePrompt.isEnabled = true
                    binding.viewGeneratingCodeBox.visibility = View.GONE
                    switchToLivePreview()
                    Toast.makeText(this@CodeStudioActivity, "Generation failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun extractHtmlFromResponse(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
            return trimmed
        }
        val match = Regex("```(?:html)?([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        return trimmed
    }

    private fun loadCodeIntoStudio(code: String, title: String) {
        currentProjectCode = code
        binding.etCodeEditor.setText(code)
        binding.tvEditorStatus.text = "index.html • ${title.take(25)}"
        deployToLiveWebView(code)
        updateProjectAnalysis(code, title)
        com.jarvis.assistant.data.code.CodeProjectManager.saveProject(this, title, code, "html")
        // Automatically switch to live preview once code is complete
        switchToLivePreview()
    }

    private fun updateProjectAnalysis(code: String, title: String) {
        val hasJs = code.contains("<script", ignoreCase = true)
        val hasCss = code.contains("<style", ignoreCase = true)
        val hasCanvas = code.contains("<canvas", ignoreCase = true)
        val hasStorage = code.contains("localStorage", ignoreCase = true)

        val analysisText = "Active Project: $title\n" +
                "Architecture: Single-page Web Component • ${code.length} characters\n" +
                "Stack: HTML5 • ${if (hasCss) "Embedded CSS3" else "Basic CSS"} • ${if (hasJs) "Vanilla JavaScript ES6+" else "Static markup"}\n" +
                "Capabilities: ${if (hasCanvas) "Canvas Graphics • " else ""}${if (hasStorage) "LocalStorage Persistence • " else "In-Memory State • "}Mobile Responsive Viewport"

        binding.tvAiAnalysisOverview.text = analysisText
    }

    private fun deployToLiveWebView(code: String) {
        val htmlToLoad = if (isPythonCode(code)) {
            wrapPythonInTerminalConsole(code)
        } else {
            code
        }

        binding.wvLivePreview.loadDataWithBaseURL(
            "https://siya-app.local",
            htmlToLoad,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun isPythonCode(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.contains("<html", ignoreCase = true)) return false
        return trimmed.startsWith("def ") || trimmed.startsWith("import ") ||
                trimmed.startsWith("class ") || trimmed.startsWith("#!/usr/bin/env python") ||
                trimmed.contains("print(")
    }

    private fun wrapPythonInTerminalConsole(pyCode: String): String {
        val escapedPy = pyCode.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Siya Python Terminal</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { background: #0D1117; color: #C9D1D9; font-family: monospace; padding: 16px; min-height: 100vh; }
        .bar { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #30363D; padding-bottom: 12px; margin-bottom: 14px; }
        .tag { background: #238636; color: white; padding: 4px 10px; border-radius: 6px; font-size: 12px; font-weight: bold; }
        .btn-run { background: #EA580C; color: white; border: none; padding: 8px 16px; border-radius: 8px; cursor: pointer; font-weight: bold; font-size: 13px; }
        pre.editor { background: #161B22; border: 1px solid #30363D; border-radius: 8px; padding: 12px; font-size: 12px; overflow-x: auto; color: #79C0FF; line-height: 1.5; max-height: 240px; }
        .term { background: #010409; border: 1px solid #30363D; border-radius: 8px; padding: 14px; margin-top: 14px; min-height: 180px; font-size: 13px; }
        .term-head { color: #8B949E; margin-bottom: 10px; font-size: 11px; text-transform: uppercase; letter-spacing: 1px; }
        .log-line { color: #58A6FF; margin-bottom: 6px; }
        .ok-line { color: #3FB950; margin-bottom: 6px; }
    </style>
</head>
<body>
    <div class="bar">
        <span class="tag">Python 3.12 Runner</span>
        <button class="btn-run" onclick="runScript()">Run Script</button>
    </div>
    <pre class="editor" id="pyCode">$escapedPy</pre>
    <div class="term">
        <div class="term-head">Interactive Terminal Console</div>
        <div id="outputArea">
            <div class="ok-line">[Siya Python Environment Ready]</div>
            <div class="log-line">Tap 'Run Script' above to execute.</div>
        </div>
    </div>
    <script>
        function runScript() {
            var out = document.getElementById('outputArea');
            out.innerHTML = '<div class="log-line">$ python main.py</div>';
            setTimeout(function() {
                var codeText = document.getElementById('pyCode').innerText;
                var lines = codeText.split('\n');
                var printed = false;
                lines.forEach(function(l) {
                    var m = l.match(/print\s*\((.*)\)/);
                    if (m) {
                        printed = true;
                        var val = m[1].replace(/['"]/g, '');
                        out.innerHTML += '<div class="ok-line">&gt; ' + val + '</div>';
                    }
                });
                if (!printed) {
                    out.innerHTML += '<div class="ok-line">&gt; Program executed successfully with exit code 0.</div>';
                }
            }, 300);
        }
    </script>
</body>
</html>"""
    }

    private fun sendGenerationNotification(projectTitle: String) {
        val intent = Intent(this, CodeStudioActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_code_studio)
            .setContentTitle("Siya Code Studio")
            .setContentText("Project '$projectTitle' generated & deployed live!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Project '$projectTitle' is live in the deployment preview. Tap to view code or download ZIP."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun exportProjectAsApk() {
        val codeToExport = binding.etCodeEditor.text.toString().ifBlank { currentProjectCode }
        if (codeToExport.isBlank()) {
            Toast.makeText(this, "No code available to export.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val result = com.jarvis.assistant.util.ApkBuilderEngine.buildApk(
                context = this@CodeStudioActivity,
                projectTitle = currentProjectTitle,
                code = codeToExport,
                language = "html"
            )

            withContext(Dispatchers.Main) {
                if (result.success && result.apkFile != null) {
                    Toast.makeText(
                        this@CodeStudioActivity,
                        "APK packaged successfully to:\n/sdcard/Download/${result.apkFile.name}",
                        Toast.LENGTH_LONG
                    ).show()

                    // Trigger direct package installer intent
                    try {
                        val apkUri = FileProvider.getUriForFile(
                            this@CodeStudioActivity,
                            "${packageName}.provider",
                            result.apkFile
                        )
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(installIntent)
                    } catch (_: Exception) {}
                } else {
                    Toast.makeText(
                        this@CodeStudioActivity,
                        "Export APK failed: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun downloadProjectZip() {
        val codeToZip = binding.etCodeEditor.text.toString().ifBlank { currentProjectCode }
        if (codeToZip.isBlank()) {
            Toast.makeText(this, "No code available to download.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val safeTitle = currentProjectTitle.replace("[^a-zA-Z0-9]".toRegex(), "_").take(20)
                val fileName = "SiyaProject_${safeTitle}_$timestamp.zip"
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                val zipFile = File(downloadDir, fileName)

                FileOutputStream(zipFile).use { fos ->
                    ZipOutputStream(fos).use { zos ->
                        // 1. index.html
                        val htmlEntry = ZipEntry("index.html")
                        zos.putNextEntry(htmlEntry)
                        zos.write(codeToZip.toByteArray(StandardCharsets.UTF_8))
                        zos.closeEntry()

                        // 2. README.md
                        val readmeText = "# Siya AI Code Studio Project\n\nGenerated on: ${java.util.Date()}\nProject: $currentProjectTitle\n\n## Running the Project\nDouble-click `index.html` in any web browser to run."
                        val readmeEntry = ZipEntry("README.md")
                        zos.putNextEntry(readmeEntry)
                        zos.write(readmeText.toByteArray(StandardCharsets.UTF_8))
                        zos.closeEntry()
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CodeStudioActivity,
                        "ZIP downloaded to:\n/sdcard/Download/$fileName",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CodeStudioActivity,
                        "Failed to save ZIP: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
