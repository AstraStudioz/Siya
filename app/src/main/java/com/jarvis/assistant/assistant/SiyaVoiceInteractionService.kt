package com.jarvis.assistant.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Android VoiceInteractionService for Siya AI.
 *
 * Allows Siya AI to be registered and chosen by the user as the system Default Digital Assistant.
 */
class SiyaVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "SiyaVoiceInteraction"
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "SiyaVoiceInteractionService is ready.")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(TAG, "SiyaVoiceInteractionService is shutting down.")
    }
}
