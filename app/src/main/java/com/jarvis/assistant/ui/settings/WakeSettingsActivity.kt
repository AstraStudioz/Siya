package com.jarvis.assistant.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.audio.VoskModelManager
import com.jarvis.assistant.audio.VoskModelStatus
import com.jarvis.assistant.databinding.ActivityWakeSettingsBinding
import com.jarvis.assistant.util.ThemeManager

/**
 * Consolidated Wake Up Detection and Assistant Settings Activity.
 *
 * Provides control over:
 * - "Hey Siya" offline wake word detection
 * - Always-on background listening
 * - Audio chime & vibration on wake
 * - Wake sensitivity (Kam / Medium / Zyada)
 * - Floating Siya popup & animation controls
 * - Silence auto-dismiss timeout (5s / 8s / 15s)
 * - Voice interruption (barge-in)
 * - System permissions (Overlay, Mic, Default Assistant, Battery Optimization)
 * - Vosk speech recognition model status
 */
class WakeSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWakeSettingsBinding
    private val preferences by lazy { JarvisApp.instance.preferences }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updateStatusRows()
        if (granted) {
            Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityWakeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)

        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateStatusRows()
    }

    private fun setupUI() {
        binding.switchWakeWord.isChecked = preferences.isWakeWordEnabled
        binding.switchAlwaysOn.isChecked = preferences.isAlwaysOnListening
        binding.switchWakeSound.isChecked = preferences.isWakeSoundEnabled
        binding.switchWakePopup.isChecked = preferences.isWakePopupEnabled
        binding.switchPopupAnimation.isChecked = preferences.isWakePopupAnimated
        binding.switchVoiceInterruption.isChecked = preferences.isVoiceInterruptionEnabled

        updateSensitivityButtons(preferences.wakeSensitivity)
        updateTimeoutButtons(preferences.wakePopupSilenceSec)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.switchWakeWord.setOnCheckedChangeListener { _, isChecked ->
            preferences.isWakeWordEnabled = isChecked
            val msg = if (isChecked) "Wake word detection active" else "Wake word detection disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            preferences.isAlwaysOnListening = isChecked
        }

        binding.switchWakeSound.setOnCheckedChangeListener { _, isChecked ->
            preferences.isWakeSoundEnabled = isChecked
        }

        binding.switchWakePopup.setOnCheckedChangeListener { _, isChecked ->
            preferences.isWakePopupEnabled = isChecked
        }

        binding.switchPopupAnimation.setOnCheckedChangeListener { _, isChecked ->
            preferences.isWakePopupAnimated = isChecked
        }

        binding.switchVoiceInterruption.setOnCheckedChangeListener { _, isChecked ->
            preferences.isVoiceInterruptionEnabled = isChecked
        }

        // Sensitivity buttons
        binding.btnSensLow.setOnClickListener {
            preferences.wakeSensitivity = "low"
            updateSensitivityButtons("low")
        }
        binding.btnSensMedium.setOnClickListener {
            preferences.wakeSensitivity = "medium"
            updateSensitivityButtons("medium")
        }
        binding.btnSensHigh.setOnClickListener {
            preferences.wakeSensitivity = "high"
            updateSensitivityButtons("high")
        }

        // Timeout buttons
        binding.btnTimeout5s.setOnClickListener {
            preferences.wakePopupSilenceSec = 5
            updateTimeoutButtons(5)
        }
        binding.btnTimeout8s.setOnClickListener {
            preferences.wakePopupSilenceSec = 8
            updateTimeoutButtons(8)
        }
        binding.btnTimeout15s.setOnClickListener {
            preferences.wakePopupSilenceSec = 15
            updateTimeoutButtons(15)
        }

        // Overlay permission
        binding.btnOverlayPermission.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }

        // Default Assistant
        binding.btnAssistantSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            try {
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Please open Android Settings > Apps > Default Apps > Digital Assistant", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Mic permission
        binding.btnMicPermission.setOnClickListener {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // Battery optimization
        binding.btnBatteryOptimization.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun updateSensitivityButtons(level: String) {
        fun setStyle(button: Button, isSelected: Boolean) {
            if (isSelected) {
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_sapphire))
                button.setTextColor(Color.WHITE)
            } else {
                button.setBackgroundColor(Color.TRANSPARENT)
                button.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }
        setStyle(binding.btnSensLow, level == "low")
        setStyle(binding.btnSensMedium, level == "medium")
        setStyle(binding.btnSensHigh, level == "high")
    }

    private fun updateTimeoutButtons(sec: Int) {
        fun setStyle(button: Button, isSelected: Boolean) {
            if (isSelected) {
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_sapphire))
                button.setTextColor(Color.WHITE)
            } else {
                button.setBackgroundColor(Color.TRANSPARENT)
                button.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }
        setStyle(binding.btnTimeout5s, sec == 5)
        setStyle(binding.btnTimeout8s, sec == 8)
        setStyle(binding.btnTimeout15s, sec == 15)
    }

    @SuppressLint("BatteryLife")
    private fun updateStatusRows() {
        // Overlay permission
        val hasOverlay = Settings.canDrawOverlays(this)
        if (hasOverlay) {
            binding.tvOverlayStatus.text = "Permission granted • Floating popup enabled"
            binding.tvOverlayStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnOverlayPermission.text = "GRANTED"
            binding.btnOverlayPermission.isEnabled = false
        } else {
            binding.tvOverlayStatus.text = "Permission required to display floating popup"
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            binding.btnOverlayPermission.text = "GRANT"
            binding.btnOverlayPermission.isEnabled = true
        }

        // Assistant status
        val assistantSetting = Settings.Secure.getString(contentResolver, "assistant") ?: ""
        val isDefaultAssistant = assistantSetting.contains(packageName)
        if (isDefaultAssistant) {
            binding.tvAssistantStatus.text = "Siya AI is active default assistant"
            binding.tvAssistantStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnAssistantSettings.text = "ACTIVE"
        } else {
            binding.tvAssistantStatus.text = "Tap to set Siya AI as default assistant"
            binding.tvAssistantStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            binding.btnAssistantSettings.text = "SET"
        }

        // Mic permission
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            binding.tvMicStatus.text = "Microphone access allowed"
            binding.tvMicStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnMicPermission.text = "ALLOWED"
            binding.btnMicPermission.isEnabled = false
        } else {
            binding.tvMicStatus.text = "Microphone permission required for wake word"
            binding.tvMicStatus.setTextColor(Color.parseColor("#EF4444"))
            binding.btnMicPermission.text = "ALLOW"
            binding.btnMicPermission.isEnabled = true
        }

        // Battery optimization
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        if (isIgnoringBattery) {
            binding.tvBatteryStatus.text = "Battery optimizations ignored (unrestricted)"
            binding.tvBatteryStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnBatteryOptimization.text = "IGNORED"
            binding.btnBatteryOptimization.isEnabled = false
        } else {
            binding.tvBatteryStatus.text = "Recommend ignoring battery optimizations for standby"
            binding.tvBatteryStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            binding.btnBatteryOptimization.text = "IGNORE"
            binding.btnBatteryOptimization.isEnabled = true
        }

        // Vosk speech model status
        val modelStatus = VoskModelManager.getSpeechModelStatus(this)
        when (modelStatus) {
            VoskModelStatus.READY -> {
                binding.tvVoskModelStatus.text = "Model loaded & ready in memory (en-us offline)"
                binding.tvVoskModelStatus.setTextColor(Color.parseColor("#10B981"))
                binding.badgeVoskModel.text = "READY"
                binding.badgeVoskModel.setTextColor(Color.parseColor("#10B981"))
                binding.badgeVoskModel.setBackgroundResource(R.drawable.bg_jarvis_status_badge_green)
            }
            VoskModelStatus.LOADING -> {
                binding.tvVoskModelStatus.text = "Unpacking or initializing offline model..."
                binding.tvVoskModelStatus.setTextColor(Color.parseColor("#F59E0B"))
                binding.badgeVoskModel.text = "LOADING"
                binding.badgeVoskModel.setTextColor(Color.parseColor("#F59E0B"))
                binding.badgeVoskModel.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
            }
            VoskModelStatus.MISSING -> {
                binding.tvVoskModelStatus.text = "Model asset missing. Wake-word cannot function offline."
                binding.tvVoskModelStatus.setTextColor(Color.parseColor("#EF4444"))
                binding.badgeVoskModel.text = "MISSING"
                binding.badgeVoskModel.setTextColor(Color.parseColor("#EF4444"))
                binding.badgeVoskModel.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
            }
            VoskModelStatus.CORRUPT -> {
                binding.tvVoskModelStatus.text = "Model binary is corrupted or incomplete."
                binding.tvVoskModelStatus.setTextColor(Color.parseColor("#EF4444"))
                binding.badgeVoskModel.text = "CORRUPT"
                binding.badgeVoskModel.setTextColor(Color.parseColor("#EF4444"))
                binding.badgeVoskModel.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
            }
        }
    }
}
