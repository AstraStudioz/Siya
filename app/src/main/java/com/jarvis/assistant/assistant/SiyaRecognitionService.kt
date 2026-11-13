package com.jarvis.assistant.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * RecognitionService compatibility stub required for Android digital assistant registration.
 *
 * Siya AI uses Gemini Live WebSocket and Vosk for speech recognition rather than the legacy
 * Android RecognitionService API. This service cleanly reports [SpeechRecognizer.ERROR_CLIENT]
 * on start requests, releases callbacks on cancel/stop, and never fabricates recognitions.
 */
class SiyaRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "SiyaRecognitionService"
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.i(TAG, "onStartListening requested; reporting ERROR_CLIENT as Siya AI uses Gemini Live directly.")
        try {
            listener?.error(SpeechRecognizer.ERROR_CLIENT)
        } catch (e: Exception) {
            Log.w(TAG, "Error reporting recognition error: ${e.message}")
        }
    }

    override fun onCancel(listener: Callback?) {
        Log.i(TAG, "onCancel received")
    }

    override fun onStopListening(listener: Callback?) {
        Log.i(TAG, "onStopListening received")
    }
}
