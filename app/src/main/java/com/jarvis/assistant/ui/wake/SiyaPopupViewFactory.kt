package com.jarvis.assistant.ui.wake

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.util.ThemeManager

/**
 * Shared factory that creates and binds the Siya AI floating popup view.
 *
 * Used by both [SiyaWakePopupController] (for WindowManager overlay)
 * and [com.jarvis.assistant.assistant.SiyaVoiceInteractionSession] (for Android Default Digital Assistant).
 *
 * Strictly adheres to the Siya AI theme system, liquid-glass card styling,
 * animated waveform, and Siya avatar/orb visuals.
 */
class SiyaPopupViewFactory(private val context: Context) {

    data class PopupViewHolder(
        val rootView: View,
        val cardView: View,
        val titleView: TextView,
        val stateLabelView: TextView,
        val transcriptView: TextView,
        val closeButton: View,
        val avatarWebView: WebView?,
        val fallbackOrb: View,
        val waveformView: SiyaWaveformView,
        val updateState: (ConversationState) -> Unit,
        val updateAudioLevel: (Float) -> Unit,
        val updateTranscript: (user: String, assistant: String) -> Unit,
        val destroy: () -> Unit
    )

    fun createPopupView(
        onCloseClicked: () -> Unit
    ): PopupViewHolder {
        val prefs = try { JarvisApp.instance.preferences } catch (_: Exception) { null }
        val themeRes = ThemeManager.resolveThemeRes(prefs)
        val themedContext = ContextThemeWrapper(context, themeRes)

        // Resolve theme attributes
        val tv = TypedValue()
        val theme = themedContext.theme

        fun resolveColor(attr: Int, defaultColor: Int): Int {
            return if (theme.resolveAttribute(attr, tv, true)) tv.data else defaultColor
        }

        val textPrimary = resolveColor(R.attr.themeTextPrimary, Color.parseColor("#0F172A"))
        val textSecondary = resolveColor(R.attr.themeTextSecondary, Color.parseColor("#475569"))
        val textMuted = resolveColor(R.attr.themeTextMuted, Color.parseColor("#64748B"))
        val cardGlassStart = resolveColor(R.attr.themeCardGlassStart, Color.parseColor("#F5FFFFFF"))
        val cardGlassEnd = resolveColor(R.attr.themeCardGlassEnd, Color.parseColor("#C8FFFFFF"))
        val cardBorder = resolveColor(R.attr.themeCardBorder, Color.parseColor("#33CBD5E1"))
        val accent1 = resolveColor(R.attr.themeAccent1, Color.parseColor("#FF6A3D"))
        val accent2 = resolveColor(R.attr.themeAccent2, Color.parseColor("#F43F5E"))
        val accent = resolveColor(R.attr.themeAccent, accent1)

        val density = themedContext.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        // Root container
        val root = FrameLayout(themedContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            clipChildren = false
            clipToPadding = false
        }

        // Liquid-glass rounded card (~28dp corners)
        val card = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            val cornerRadiusPx = dp(28).toFloat()
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(cardGlassStart, cardGlassEnd)
            ).apply {
                cornerRadius = cornerRadiusPx
                setStroke(dp(1), cardBorder)
            }
            elevation = dp(16).toFloat()
            setPadding(dp(18), dp(16), dp(18), dp(16))
            clipChildren = false
            clipToPadding = false
        }

        val cardParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
            bottomMargin = dp(14)
            topMargin = dp(8)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        root.addView(card, cardParams)

        // Top Row: Avatar + Title & State + Close Button
        val topRow = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        card.addView(topRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Avatar / Orb container (~64dp x 64dp)
        val avatarContainer = FrameLayout(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            clipChildren = false
            clipToPadding = false
        }
        topRow.addView(avatarContainer)

        // Glowing circular halo behind avatar
        val haloView = View(themedContext).apply {
            val haloColor = Color.argb(
                (60 * (prefs?.orbGlow ?: 1.0f)).toInt().coerceIn(20, 140),
                Color.red(accent1), Color.green(accent1), Color.blue(accent1)
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(haloColor)
            }
            alpha = 0.85f
        }
        avatarContainer.addView(haloView, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER))

        // Native fallback orb
        val fallbackOrb = View(themedContext).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(accent1, accent2, accent)
            ).apply {
                shape = GradientDrawable.OVAL
            }
            visibility = View.VISIBLE
        }
        avatarContainer.addView(fallbackOrb, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))

        // Siya Character WebView loading overlay-toys/siya.html
        var avatarWebView: WebView? = null
        try {
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(themedContext))
                .build()

            val wv = WebView(themedContext).apply {
                setBackgroundColor(Color.TRANSPARENT)
                contentDescription = "Siya AI Character"
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = false
                }
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        assetLoader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Once loaded, hide fallback orb and show avatar
                        fallbackOrb.visibility = View.GONE
                        visibility = View.VISIBLE
                    }
                }
                visibility = View.INVISIBLE
                loadUrl("https://appassets.androidplatform.net/assets/overlay-toys/siya.html")
            }
            avatarWebView = wv
            avatarContainer.addView(wv, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER))
        } catch (_: Throwable) {
            fallbackOrb.visibility = View.VISIBLE
        }

        // Title and Status column
        val titleCol = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        topRow.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val titleText = TextView(themedContext).apply {
            text = "Siya AI"
            setTextColor(textPrimary)
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
        }
        titleCol.addView(titleText)

        val stateLabel = TextView(themedContext).apply {
            text = "I'm listening..."
            setTextColor(textSecondary)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT
            setPadding(0, dp(2), 0, 0)
        }
        titleCol.addView(stateLabel)

        // Close button (48dp x 48dp minimum touch target)
        val closeBtn = FrameLayout(themedContext).apply {
            val size = dp(48)
            layoutParams = LinearLayout.LayoutParams(size, size)
            isClickable = true
            isFocusable = true
            contentDescription = "Close Siya AI popup"

            val outVal = TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true)) {
                setBackgroundResource(outVal.resourceId)
            }

            val icon = TextView(themedContext).apply {
                text = "✕"
                setTextColor(textMuted)
                textSize = 18f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            addView(icon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            setOnClickListener { onCloseClicked() }
        }
        topRow.addView(closeBtn)

        // Live transcript / speech text area (subtle)
        val transcriptText = TextView(themedContext).apply {
            setTextColor(textSecondary)
            textSize = 12f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
        }
        card.addView(transcriptText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Waveform bottom row
        val waveform = SiyaWaveformView(themedContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(32)
            ).apply {
                topMargin = dp(8)
            }
        }
        card.addView(waveform)

        // Dynamic State Updater
        val updateStateFunc: (ConversationState) -> Unit = { state ->
            val label = when (state) {
                ConversationState.LISTENING -> "I'm listening..."
                ConversationState.THINKING -> "Thinking..."
                ConversationState.SPEAKING -> "Siya is speaking..."
                ConversationState.IDLE -> "Connecting..."
            }
            stateLabel.text = label

            // Update WebView avatar
            avatarWebView?.let { wv ->
                try {
                    wv.evaluateJavascript("window.setState && window.setState('${state.orbKey}')", null)
                } catch (_: Exception) {}
            }
        }

        val updateAudioLevelFunc: (Float) -> Unit = { level ->
            waveform.setAudioLevel(level)
        }

        val updateTranscriptFunc: (String, String) -> Unit = { user, assistant ->
            val activeText = assistant.ifBlank { user }
            if (activeText.isNotBlank()) {
                transcriptText.text = activeText
                transcriptText.visibility = View.VISIBLE
            } else {
                transcriptText.visibility = View.GONE
            }
        }

        val destroyFunc: () -> Unit = {
            try {
                avatarWebView?.let { wv ->
                    wv.stopLoading()
                    wv.destroy()
                }
            } catch (_: Exception) {}
        }

        return PopupViewHolder(
            rootView = root,
            cardView = card,
            titleView = titleText,
            stateLabelView = stateLabel,
            transcriptView = transcriptText,
            closeButton = closeBtn,
            avatarWebView = avatarWebView,
            fallbackOrb = fallbackOrb,
            waveformView = waveform,
            updateState = updateStateFunc,
            updateAudioLevel = updateAudioLevelFunc,
            updateTranscript = updateTranscriptFunc,
            destroy = destroyFunc
        )
    }
}
