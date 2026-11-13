package com.jarvis.assistant.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.jarvis.assistant.databinding.FragmentUpdatesBinding

class UpdatesFragment : Fragment() {

    private var _binding: FragmentUpdatesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUpdatesBinding.inflate(inflater, container, false)
        renderFeatureUpdates()
        return binding.root
    }

    private data class FeatureNotice(val feature: String, val title: String, val details: String, val kind: String = "FEATURE")

    private fun renderFeatureUpdates() {
        val features = listOf(
            FeatureNotice("Google Antigravity Core", "MCP Architecture for Autonomous Code, Image & Video", "Integrated Model Context Protocol client allowing Siya AI to delegate complex coding, tool execution, and multimedia tasks directly to Antigravity agents with zero limits.", "NEW IN v5.0"),
            FeatureNotice("Instant On-Device APK Compiler", "Build Installable Android APKs Directly on Phone", "Convert any generated HTML5/JS or Python web project into a standalone Android APK directly in your Download folder with a 1-tap installation alert.", "NEW IN v5.0"),
            FeatureNotice("Precision Live Camera & Vision", "Immediate High-Res Frame Capture & Zero Babbling", "Completely resolved unsolicited speech on camera start. Visual snapshots are delivered with forced real-time priority, ensuring accurate answers without excuses.", "NEW IN v5.0"),
            FeatureNotice("In-App Auto-Update Engine", "1-Tap GitHub Releases Download & Progress Bar", "Detects newly published APK releases immediately from AstraStudioz/Siya. Download and install seamless updates within the app with progress monitoring.", "NEW IN v5.0"),
            FeatureNotice("Multi-Provider Engine", "OpenAI DALL-E 3 & Groq Cloud Code Studio", "Full multi-provider architecture for Image and Code generation. Switch between Groq (Llama 3.3 70B & DeepSeek R1), OpenAI (DALL-E 3 & GPT-4o), Google Gemini, and Custom providers with direct API key links.", "UPGRADED"),
            FeatureNotice("ChatGPT History & Notification Reader", "Images Gallery & On-Demand Phone Notification Alerts", "Full slide-out history drawer for chats, generated images, and projects, plus hands-free notification reading.", "UPGRADED")
        )
        val list = binding.updatesList
        list.removeAllViews()
        features.forEach { feature ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                background = androidx.core.content.ContextCompat.getDrawable(requireContext(), com.jarvis.assistant.R.drawable.bg_jarvis_card)
                setPadding(dp(18), dp(18), dp(18), dp(18))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(14)
                }
            }
            card.addView(TextView(requireContext()).apply {
                text = feature.feature.uppercase() + if (feature.kind == "DIAGNOSTIC") "  ·  DIAGNOSTIC" else ""
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(themeColor(com.jarvis.assistant.R.attr.themeAccent))
            })
            card.addView(TextView(requireContext()).apply {
                text = feature.title
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(themeColor(com.jarvis.assistant.R.attr.themeTextPrimary))
                setPadding(0, dp(8), 0, dp(5))
            })
            card.addView(TextView(requireContext()).apply {
                text = feature.details
                textSize = 13f
                setTextColor(themeColor(com.jarvis.assistant.R.attr.themeTextMuted))
                setLineSpacing(dp(2).toFloat(), 1f)
            })
            list.addView(card)
        }
    }

    private fun themeColor(attribute: Int): Int {
        val value = TypedValue()
        requireContext().theme.resolveAttribute(attribute, value, true)
        return value.data
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
        setupCheckUpdatesButton()
        observeAvailableUpdate()
    }

    private fun setupCheckUpdatesButton() {
        binding.btnCheckUpdatesManual.setOnClickListener {
            binding.btnCheckUpdatesManual.isEnabled = false
            binding.btnCheckUpdatesManual.text = "Checking..."
            viewLifecycleOwner.lifecycleScope.launch {
                val result = com.jarvis.assistant.update.UpdateManager.checkForUpdate(isManual = true)
                binding.btnCheckUpdatesManual.isEnabled = true
                binding.btnCheckUpdatesManual.text = "Check Updates"
                when (result) {
                    is com.jarvis.assistant.data.update.UpdateCheckResult.UpdateAvailable -> {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "New update v${result.updateInfo.versionName} is available!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    is com.jarvis.assistant.data.update.UpdateCheckResult.UpToDate -> {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Siya AI is up to date (v${com.jarvis.assistant.BuildConfig.VERSION_NAME})",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    is com.jarvis.assistant.data.update.UpdateCheckResult.Error -> {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Update check failed: ${result.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun observeAvailableUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                com.jarvis.assistant.update.UpdateManager.availableUpdateFlow.collect { updateInfo ->
                    val info = updateInfo ?: com.jarvis.assistant.JarvisApp.instance.preferences.getCachedUpdateInfo()
                    val isNewer = info != null && info.versionCode > com.jarvis.assistant.BuildConfig.VERSION_CODE
                    if (isNewer && info != null) {
                        binding.cardUpdateBanner.visibility = View.VISIBLE
                        binding.tvBannerVersion.text = "v${info.versionName} (Build ${info.versionCode})"
                        binding.tvBannerTitle.text = "Siya AI update v${info.versionName} available"
                        binding.tvBannerChangelog.text = info.changelog.ifBlank { "Performance improvements and neural stability updates." }

                        binding.btnBannerUpdateNow.setOnClickListener {
                            val bottomSheet = com.jarvis.assistant.update.UpdateDownloadBottomSheet(info)
                            bottomSheet.show(parentFragmentManager, "UpdateDownloadBottomSheet")
                        }

                        binding.btnBannerDismiss.setOnClickListener {
                            binding.cardUpdateBanner.visibility = View.GONE
                        }
                    } else {
                        binding.cardUpdateBanner.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
            val cached = com.jarvis.assistant.JarvisApp.instance.preferences.getCachedUpdateInfo()
            if (cached != null && cached.versionCode > com.jarvis.assistant.BuildConfig.VERSION_CODE) {
                binding.cardUpdateBanner.visibility = View.VISIBLE
                binding.tvBannerVersion.text = "v${cached.versionName} (Build ${cached.versionCode})"
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            binding.root.visibility = View.VISIBLE
            com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.root)
        } else if (hidden && _binding != null) {
            binding.root.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
