package com.jarvis.assistant.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.ui.wake.SiyaPopupViewFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android VoiceInteractionSession presenting the unified Siya AI popup UI.
 *
 * Reuses [SiyaPopupViewFactory] to present the identical Siya liquid-glass card,
 * character avatar/orb, and animated waveform.
 */
class SiyaVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "SiyaVoiceSession"
    }

    private val viewFactory = SiyaPopupViewFactory(context)
    private var viewHolder: SiyaPopupViewFactory.PopupViewHolder? = null
    private var startedByThisSession = false
    private var sessionScope: CoroutineScope? = null

    override fun onCreateContentView(): View {
        val holder = viewFactory.createPopupView(
            onCloseClicked = {
                hide()
            }
        )
        viewHolder = holder
        return holder.rootView
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.i(TAG, "SiyaVoiceInteractionSession onShow")

        val isAlreadyOn = JarvisConversationService.isSessionOn.value
        if (!isAlreadyOn) {
            startedByThisSession = true
            JarvisConversationService.startSession(context)
        } else {
            startedByThisSession = false
        }

        viewHolder?.let { holder ->
            startStateCollection(holder)
        }
    }

    private fun startStateCollection(holder: SiyaPopupViewFactory.PopupViewHolder) {
        sessionScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        sessionScope = scope

        scope.launch {
            JarvisConversationService.conversationState.collect { state ->
                holder.updateState(state)
            }
        }

        scope.launch {
            JarvisConversationService.audioLevel.collect { level ->
                holder.updateAudioLevel(level)
            }
        }

        scope.launch {
            JarvisConversationService.liveUserTranscript.collect { userText ->
                val assistantText = JarvisConversationService.liveAssistantResponse.value
                holder.updateTranscript(userText, assistantText)
            }
        }

        scope.launch {
            JarvisConversationService.liveAssistantResponse.collect { assistantText ->
                val userText = JarvisConversationService.liveUserTranscript.value
                holder.updateTranscript(userText, assistantText)
            }
        }
    }

    override fun onHide() {
        super.onHide()
        Log.i(TAG, "SiyaVoiceInteractionSession onHide")
        sessionScope?.cancel()
        sessionScope = null

        if (startedByThisSession) {
            startedByThisSession = false
            JarvisConversationService.stopSession(context)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionScope?.cancel()
        sessionScope = null
        viewHolder?.destroy()
        viewHolder = null
    }
}
