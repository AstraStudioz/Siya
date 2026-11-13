package com.jarvis.assistant.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.ui.home.MainActivity
import com.jarvis.assistant.ui.permission.PermissionActivity
import com.jarvis.assistant.util.PermissionHelper
import com.jarvis.assistant.ui.settings.ApiConnectionActivity

/**
 * Android ASSIST and VOICE_COMMAND entry-point activity for Siya AI.
 *
 * Runs transparently without history or recents footprint.
 * Validates permissions and API configuration, initiates the conversation session,
 * and immediately finishes to ensure zero task residue.
 */
class SiyaAssistActivity : Activity() {

    companion object {
        private const val TAG = "SiyaAssistActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SiyaAssistActivity triggered: ${intent?.action}")

        val prefs = try { JarvisApp.instance.preferences } catch (_: Exception) { null }
        val apiKey = prefs?.apiKey?.trim() ?: ""

        if (apiKey.isBlank()) {
            Log.w(TAG, "Gemini API key is not configured. Redirecting to ApiConnectionActivity.")
            val settingsIntent = Intent(this, ApiConnectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(settingsIntent)
            finish()
            return
        }

        if (!PermissionHelper.hasAllRequiredPermissions(this)) {
            Log.w(TAG, "Required permissions not granted. Redirecting to PermissionActivity.")
            val permIntent = Intent(this, PermissionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(permIntent)
            finish()
            return
        }

        // Start active conversation session
        JarvisConversationService.startSession(this)
        finish()
    }
}
