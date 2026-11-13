package com.jarvis.assistant.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.chat.ChatSessionEntity
import com.jarvis.assistant.data.code.CodeProjectManager
import com.jarvis.assistant.data.code.SavedCodeProject
import com.jarvis.assistant.databinding.BottomSheetChatHistoryBinding
import com.jarvis.assistant.databinding.DialogChatImagesGalleryBinding
import com.jarvis.assistant.databinding.DialogChatProjectsListBinding
import com.jarvis.assistant.databinding.DialogCodePreviewBinding
import com.jarvis.assistant.databinding.DialogImageFullPreviewBinding
import com.jarvis.assistant.databinding.ItemChatProjectBinding
import com.jarvis.assistant.databinding.ItemGalleryImageBinding
import com.jarvis.assistant.network.ImageGenClient
import com.jarvis.assistant.ui.code.CodeStudioActivity
import com.jarvis.assistant.ui.settings.SettingsActivity
import com.jarvis.assistant.util.AttachmentProcessor
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatHistoryBottomSheet(
    private val onSessionSelected: (ChatSessionEntity) -> Unit,
    private val onNewSessionRequested: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetChatHistoryBinding? = null
    private val binding get() = _binding!!
    private val chatRepository get() = JarvisApp.instance.chatRepository

    private lateinit var sessionAdapter: ChatSessionAdapter
    private var allSessionsList: List<ChatSessionEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetChatHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionAdapter = ChatSessionAdapter(
            onSessionClick = { session ->
                onSessionSelected(session)
                dismiss()
            },
            onOptionsClick = { session, anchorView ->
                showSessionOptionsMenu(session, anchorView)
            }
        )

        binding.rvChatSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChatSessions.adapter = sessionAdapter

        // Quick Navigation items (ChatGPT Style)
        updateCounts()

        binding.navImages.setOnClickListener {
            showImagesGalleryDialog()
        }

        binding.navProjects.setOnClickListener {
            showProjectsListDialog()
        }

        // Search Toggle & Filter
        binding.btnSearchHistory.setOnClickListener {
            if (binding.etSearchQuery.visibility == View.VISIBLE) {
                binding.etSearchQuery.visibility = View.GONE
                binding.etSearchQuery.setText("")
                sessionAdapter.submitList(allSessionsList)
            } else {
                binding.etSearchQuery.visibility = View.VISIBLE
                binding.etSearchQuery.requestFocus()
            }
        }

        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                val filtered = if (query.isEmpty()) {
                    allSessionsList
                } else {
                    allSessionsList.filter { it.title.contains(query, ignoreCase = true) }
                }
                sessionAdapter.submitList(filtered)
                binding.tvEmptyHistory.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                binding.rvChatSessions.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Bottom Bar Actions
        binding.btnBottomNewChat.setOnClickListener {
            onNewSessionRequested()
            dismiss()
        }

        binding.btnBottomSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
            dismiss()
        }

        binding.btnBottomVoice.setOnClickListener {
            JarvisConversationService.startSession(requireContext())
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatRepository.getAllSessionsFlow().collect { sessions ->
                    allSessionsList = sessions
                    sessionAdapter.submitList(sessions)
                    binding.tvEmptyHistory.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvChatSessions.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun updateCounts() {
        val projects = CodeProjectManager.getAllProjects(requireContext())
        binding.tvProjectsCount.text = "${projects.size} items"

        val mediaDir = AttachmentProcessor.getMediaDir(requireContext())
        val imgCount = mediaDir.listFiles()?.count { it.isFile && (it.name.endsWith(".jpg") || it.name.endsWith(".png")) } ?: 0
        binding.tvImagesCount.text = "$imgCount images"
    }

    private fun showImagesGalleryDialog() {
        val dialogBinding = DialogChatImagesGalleryBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val mediaDir = AttachmentProcessor.getMediaDir(requireContext())
        val imageFiles = mediaDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".jpg", ignoreCase = true) || it.name.endsWith(".png", ignoreCase = true))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (imageFiles.isEmpty()) {
            dialogBinding.tvEmptyGallery.visibility = View.VISIBLE
            dialogBinding.rvImagesGallery.visibility = View.GONE
        } else {
            dialogBinding.tvEmptyGallery.visibility = View.GONE
            dialogBinding.rvImagesGallery.visibility = View.VISIBLE
            dialogBinding.rvImagesGallery.layoutManager = LinearLayoutManager(requireContext())
            dialogBinding.rvImagesGallery.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val itemBinding = ItemGalleryImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                    return object : RecyclerView.ViewHolder(itemBinding.root) {}
                }

                override fun getItemCount(): Int = imageFiles.size

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val file = imageFiles[position]
                    val itemBinding = ItemGalleryImageBinding.bind(holder.itemView)
                    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                    itemBinding.tvImageTitle.text = file.nameWithoutExtension.replace("siya_img_", "Image ")
                    itemBinding.tvImageDate.text = sdf.format(Date(file.lastModified()))

                    try {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                        itemBinding.ivThumbnail.setImageBitmap(bmp)
                    } catch (_: Exception) {}

                    itemBinding.root.setOnClickListener {
                        showFullImageDialog(file)
                    }
                }
            }
        }

        dialogBinding.btnCloseGallery.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showFullImageDialog(file: File) {
        val previewBinding = DialogImageFullPreviewBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(previewBinding.root)
            .create()

        previewBinding.tvImagePreviewTitle.text = file.nameWithoutExtension
        try {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            previewBinding.ivFullPreview.setImageBitmap(bmp)
        } catch (_: Exception) {}

        previewBinding.btnCloseImagePreview.setOnClickListener {
            dialog.dismiss()
        }

        previewBinding.btnShareImage.setOnClickListener {
            try {
                val shareIntent = ImageGenClient().createShareIntent(requireContext(), file)
                startActivity(Intent.createChooser(shareIntent, "Share Image"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        previewBinding.btnSaveToGallery.setOnClickListener {
            try {
                val uri = ImageGenClient().saveImageToGallery(requireContext(), file)
                if (uri != null) {
                    Toast.makeText(requireContext(), "Image saved to device Pictures!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Could not save to gallery", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showProjectsListDialog() {
        val dialogBinding = DialogChatProjectsListBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val projects = CodeProjectManager.getAllProjects(requireContext())
        if (projects.isEmpty()) {
            dialogBinding.tvEmptyProjects.visibility = View.VISIBLE
            dialogBinding.rvProjectsList.visibility = View.GONE
        } else {
            dialogBinding.tvEmptyProjects.visibility = View.GONE
            dialogBinding.rvProjectsList.visibility = View.VISIBLE
            dialogBinding.rvProjectsList.layoutManager = LinearLayoutManager(requireContext())
            dialogBinding.rvProjectsList.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val itemBinding = ItemChatProjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                    return object : RecyclerView.ViewHolder(itemBinding.root) {}
                }

                override fun getItemCount(): Int = projects.size

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val project = projects[position]
                    val itemBinding = ItemChatProjectBinding.bind(holder.itemView)
                    itemBinding.tvProjectTitle.text = project.title
                    itemBinding.tvProjectLanguage.text = project.language.uppercase(Locale.getDefault())
                    val snippet = project.code.lines().filter { it.isNotBlank() }.take(3).joinToString("\n")
                    itemBinding.tvProjectSnippet.text = snippet

                    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                    itemBinding.tvProjectDate.text = sdf.format(Date(project.timestamp))

                    itemBinding.root.setOnClickListener {
                        showCodePreviewDialog(project)
                    }
                }
            }
        }

        dialogBinding.btnCloseProjects.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showCodePreviewDialog(project: SavedCodeProject) {
        val previewBinding = DialogCodePreviewBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(previewBinding.root)
            .create()

        previewBinding.tvPreviewTitle.text = project.title
        previewBinding.tvCodeContent.text = project.code

        previewBinding.btnCopyCode.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(project.title, project.code))
            Toast.makeText(requireContext(), "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        previewBinding.btnOpenInStudio.setOnClickListener {
            dialog.dismiss()
            dismiss()
            val intent = Intent(requireContext(), CodeStudioActivity::class.java).apply {
                putExtra("extra_existing_code", project.code)
                putExtra("extra_project_title", project.title)
            }
            startActivity(intent)
        }

        previewBinding.btnClosePreview.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSessionOptionsMenu(session: ChatSessionEntity, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        val pinTitle = if (session.isPinned) "Unpin Chat" else "Pin Chat"
        popup.menu.add(0, 1, 0, pinTitle)
        popup.menu.add(0, 2, 1, "Rename")
        popup.menu.add(0, 3, 2, "Delete")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    chatRepository.setSessionPinned(session.id, !session.isPinned)
                    true
                }
                2 -> {
                    showRenameDialog(session)
                    true
                }
                3 -> {
                    showDeleteConfirmDialog(session)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameDialog(session: ChatSessionEntity) {
        val input = EditText(requireContext()).apply {
            setText(session.title)
            setSelection(session.title.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename Chat")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    chatRepository.renameSession(session.id, newTitle)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmDialog(session: ChatSessionEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete '${session.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                chatRepository.deleteSession(session.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
