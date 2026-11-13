package com.jarvis.assistant.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Service that creates active [SiyaVoiceInteractionSession] instances when invoked by Android.
 */
class SiyaVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return SiyaVoiceInteractionSession(this)
    }
}
