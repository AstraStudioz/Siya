package com.jarvis.assistant.ui.wake

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Controller for the Siya AI floating overlay popup triggered on wake word detection.
 *
 * Owned and managed by [JarvisConversationService].
 * Handles WindowManager attachment, lifecycle, real-time StateFlow collection,
 * silence timeouts, and safe dismissal.
 */
class SiyaWakePopupController(
    private val context: Context,
    private val onStandbyRequested: () -> Unit
) {
    companion object {
        private const val TAG = "SiyaWakePopup"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val viewFactory = SiyaPopupViewFactory(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentViewHolder: SiyaPopupViewFactory.PopupViewHolder? = null
    private var isAttached = false
    private var wakeStartedSession = false

    private var collectionScope: CoroutineScope? = null
    private var silenceRunnable: Runnable? = null

    /**
     * Shows the floating wake popup if overlay permission is granted and popup is enabled.
     *
     * @param isWakeTriggered true if the active conversation was started via "Hey Siya" wake word
     */
    fun show(isWakeTriggered: Boolean) {
        val prefs = try { JarvisApp.instance.preferences } catch (_: Exception) { null }
        if (prefs?.isWakePopupEnabled == false) {
            Log.d(TAG, "Wake popup is disabled in preferences.")
            return
        }

        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Skipping floating popup.")
            return
        }

        if (windowManager == null) return

        this.wakeStartedSession = isWakeTriggered

        mainHandler.post {
            try {
                if (isAttached && currentViewHolder != null) {
                    // Already showing; reset silence timer
                    resetSilenceTimer()
                    return@post
                }

                // Clean up any stale view
                dismissInternal(triggerStandby = false)

                val holder = viewFactory.createPopupView(
                    onCloseClicked = {
                        Log.i(TAG, "Popup close (✕) button clicked.")
                        dismiss(triggerStandby = wakeStartedSession)
                    }
                )

                val displayMetrics = context.resources.displayMetrics
                val density = displayMetrics.density
                val maxCardWidthPx = (360 * density).toInt()
                val targetWidth = minOf(maxCardWidthPx, displayMetrics.widthPixels - (24 * density).toInt())

                val layoutParams = WindowManager.LayoutParams(
                    targetWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = (24 * density).toInt()
                    if (prefs?.isWakePopupAnimated != false) {
                        windowAnimations = android.R.style.Animation_Dialog
                    }
                }

                windowManager.addView(holder.rootView, layoutParams)
                currentViewHolder = holder
                isAttached = true
                Log.i(TAG, "Siya AI floating popup displayed on screen.")

                startStateCollection(holder)
                resetSilenceTimer()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display floating popup: ${e.message}", e)
                isAttached = false
                currentViewHolder = null
            }
        }
    }

    /**
     * Resets the silence timeout timer based on user or assistant activity.
     */
    fun resetSilenceTimer() {
        silenceRunnable?.let { mainHandler.removeCallbacks(it) }

        val prefs = try { JarvisApp.instance.preferences } catch (_: Exception) { null }
        val timeoutSec = (prefs?.wakePopupSilenceSec ?: 8).coerceIn(5, 30)
        val timeoutMs = timeoutSec * 1000L

        val runnable = Runnable {
            Log.i(TAG, "Popup silence timeout expired (${timeoutSec}s). Closing popup.")
            dismiss(triggerStandby = wakeStartedSession)
        }
        silenceRunnable = runnable
        mainHandler.postDelayed(runnable, timeoutMs)
    }

    private fun startStateCollection(holder: SiyaPopupViewFactory.PopupViewHolder) {
        collectionScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        collectionScope = scope

        // Collect conversation state
        scope.launch {
            JarvisConversationService.conversationState.collect { state ->
                holder.updateState(state)
                if (state == ConversationState.THINKING || state == ConversationState.SPEAKING || state == ConversationState.LISTENING) {
                    resetSilenceTimer()
                }
            }
        }

        // Collect audio level
        scope.launch {
            JarvisConversationService.audioLevel.collect { level ->
                holder.updateAudioLevel(level)
                if (level > 0.12f) {
                    resetSilenceTimer()
                }
            }
        }

        // Collect transcripts
        scope.launch {
            JarvisConversationService.liveUserTranscript.collect { userText ->
                val assistantText = JarvisConversationService.liveAssistantResponse.value
                holder.updateTranscript(userText, assistantText)
                if (userText.isNotBlank()) {
                    resetSilenceTimer()
                }
            }
        }

        scope.launch {
            JarvisConversationService.liveAssistantResponse.collect { assistantText ->
                val userText = JarvisConversationService.liveUserTranscript.value
                holder.updateTranscript(userText, assistantText)
                if (assistantText.isNotBlank()) {
                    resetSilenceTimer()
                }
            }
        }

        // Auto-dismiss on error or session end
        scope.launch {
            JarvisConversationService.isSessionOn.collect { isSessionOn ->
                if (!isSessionOn && isAttached) {
                    dismiss(triggerStandby = false)
                }
            }
        }
    }

    /**
     * Dismisses the popup, optionally requesting standby transition.
     */
    fun dismiss(triggerStandby: Boolean = false) {
        mainHandler.post {
            dismissInternal(triggerStandby)
        }
    }

    private fun dismissInternal(triggerStandby: Boolean) {
        silenceRunnable?.let { mainHandler.removeCallbacks(it) }
        silenceRunnable = null

        collectionScope?.cancel()
        collectionScope = null

        val holder = currentViewHolder
        if (holder != null && isAttached) {
            try {
                windowManager?.removeViewImmediate(holder.rootView)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing popup view: ${e.message}")
            } finally {
                holder.destroy()
            }
        }

        currentViewHolder = null
        isAttached = false

        if (triggerStandby) {
            onStandbyRequested()
        }
    }

    fun destroy() {
        dismiss(triggerStandby = false)
    }
}
