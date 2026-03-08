package com.rehaan.bluetoothchat.ui.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.rehaan.bluetoothchat.R
import com.rehaan.bluetoothchat.adapters.MessageAdapter
import com.rehaan.bluetoothchat.databinding.ActivityChatBinding
import com.rehaan.bluetoothchat.domain.model.MessageDirection
import com.rehaan.bluetoothchat.domain.model.MessageType
import com.rehaan.bluetoothchat.utils.Constants
import com.rehaan.bluetoothchat.utils.FileUtils
import com.rehaan.bluetoothchat.utils.gone
import com.rehaan.bluetoothchat.utils.showToast
import com.rehaan.bluetoothchat.utils.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private var isRecording = false

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleFilePicked(it) }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImagePicked(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deviceAddress = intent.getStringExtra(Constants.EXTRA_DEVICE_ADDRESS) ?: run {
            showToast("Invalid device")
            finish()
            return
        }
        val deviceName = intent.getStringExtra(Constants.EXTRA_DEVICE_NAME) ?: "Unknown Device"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel.initialize(deviceAddress, deviceName)

        setupRecyclerView()
        setupInputArea()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(
            onVoicePlayClicked = { filePath, messageId ->
                playVoiceMessage(filePath, messageId)
            },
            onImageClicked = { filePath ->
                // Open image viewer
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(filePath), "image/*")
                }
                try { startActivity(intent) } catch (e: Exception) { showToast("No image viewer found") }
            }
        )

        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.apply {
            this.layoutManager = layoutManager
            adapter = messageAdapter
        }
    }

    private fun setupInputArea() {
        binding.etMessage.addTextChangedListener {
            val hasText = it?.isNotBlank() == true
            binding.btnSend.visibility = if (hasText) View.VISIBLE else View.GONE
            binding.btnVoice.visibility = if (hasText) View.GONE else View.VISIBLE
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendTextMessage(text)
                binding.etMessage.setText("")
            }
        }

        binding.btnAttachment.setOnClickListener {
            showAttachmentOptions()
        }

        binding.btnVoice.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startVoiceRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopVoiceRecording(event.action == MotionEvent.ACTION_CANCEL)
                    true
                }
                else -> false
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.deviceName.collect { name ->
                supportActionBar?.title = name
            }
        }

        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                messageAdapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        binding.rvMessages.smoothScrollToPosition(messages.size - 1)
                    }
                }
                viewModel.markMessagesAsRead()
            }
        }

        lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                updateConnectionStatusUI(state)
            }
        }

        lifecycleScope.launch {
            viewModel.fileTransferProgress.collect { (progress, fileName) ->
                updateFileTransferUI(progress, fileName)
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { errorMsg ->
                Snackbar.make(binding.root, errorMsg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun updateConnectionStatusUI(state: Int) {
        when (state) {
            Constants.STATE_NONE -> {
                binding.tvConnectionStatus.text = "Disconnected"
                binding.tvConnectionStatus.setTextColor(getColor(R.color.status_disconnected))
                binding.connectionStatusBar.setBackgroundColor(getColor(R.color.status_disconnected))
                binding.layoutInputArea.alpha = 0.5f
                binding.layoutInputArea.isEnabled = false
                binding.etMessage.isEnabled = false
                binding.btnSend.isEnabled = false
            }
            Constants.STATE_LISTEN -> {
                binding.tvConnectionStatus.text = "Waiting for connection..."
                binding.tvConnectionStatus.setTextColor(getColor(R.color.status_connecting))
                binding.connectionStatusBar.setBackgroundColor(getColor(R.color.status_connecting))
            }
            Constants.STATE_CONNECTING -> {
                binding.tvConnectionStatus.text = "Connecting..."
                binding.tvConnectionStatus.setTextColor(getColor(R.color.status_connecting))
                binding.connectionStatusBar.setBackgroundColor(getColor(R.color.status_connecting))
            }
            Constants.STATE_CONNECTED -> {
                binding.tvConnectionStatus.text = "Connected"
                binding.tvConnectionStatus.setTextColor(getColor(R.color.status_connected))
                binding.connectionStatusBar.setBackgroundColor(getColor(R.color.status_connected))
                binding.layoutInputArea.alpha = 1.0f
                binding.layoutInputArea.isEnabled = true
                binding.etMessage.isEnabled = true
                binding.btnSend.isEnabled = true
            }
        }
    }

    private fun updateFileTransferUI(progress: Int, fileName: String) {
        if (progress < 100) {
            binding.layoutFileProgress.visible()
            binding.tvFileProgressName.text = fileName
            binding.progressFileTransfer.progress = progress
            binding.tvFileProgressPercent.text = "$progress%"
        } else {
            binding.layoutFileProgress.gone()
        }
    }

    private fun showAttachmentOptions() {
        val options = arrayOf("Image", "Document / File", "Audio")
        MaterialAlertDialogBuilder(this)
            .setTitle("Send Attachment")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> imagePickerLauncher.launch("image/*")
                    1 -> filePickerLauncher.launch("*/*")
                    2 -> filePickerLauncher.launch("audio/*")
                }
            }
            .show()
    }

    private fun handleFilePicked(uri: Uri) {
        val fileName = FileUtils.getFileName(this, uri)
        val fileSize = FileUtils.getFileSize(this, uri)
        val mimeType = FileUtils.getMimeType(this, uri)

        if (!FileUtils.isFileSizeAllowed(fileSize)) {
            showToast("File too large. Max ${Constants.MAX_FILE_SIZE_MB}MB")
            return
        }

        val data = FileUtils.readBytesFromUri(this, uri) ?: run {
            showToast("Failed to read file")
            return
        }

        viewModel.sendFile(fileName, mimeType, data)
    }

    private fun handleImagePicked(uri: Uri) {
        val fileName = FileUtils.getFileName(this, uri)
        val fileSize = FileUtils.getFileSize(this, uri)
        val mimeType = FileUtils.getMimeType(this, uri)

        if (!FileUtils.isFileSizeAllowed(fileSize)) {
            showToast("Image too large. Max ${Constants.MAX_FILE_SIZE_MB}MB")
            return
        }

        val data = FileUtils.readBytesFromUri(this, uri) ?: run {
            showToast("Failed to read image")
            return
        }

        viewModel.sendFile(fileName, mimeType, data)
    }

    private fun startVoiceRecording() {
        if (!viewModel.isConnected) {
            showToast("Not connected")
            return
        }
        val started = viewModel.voiceRecorder.startRecording()
        if (started) {
            isRecording = true
            binding.tvVoiceRecordingHint.visible()
            binding.btnVoice.setImageResource(R.drawable.ic_mic_active)
        } else {
            showToast("Failed to start recording")
        }
    }

    private fun stopVoiceRecording(cancelled: Boolean) {
        if (!isRecording) return
        isRecording = false
        binding.tvVoiceRecordingHint.gone()
        binding.btnVoice.setImageResource(R.drawable.ic_mic)

        if (cancelled) {
            viewModel.voiceRecorder.cancelRecording()
            return
        }

        val result = viewModel.voiceRecorder.stopRecording() ?: run {
            showToast("Recording failed")
            return
        }

        if (result.durationSeconds < 1) {
            showToast("Recording too short")
            result.file.delete()
            return
        }

        viewModel.sendVoiceMessage(result.file.absolutePath, result.durationSeconds)
    }

    private fun playVoiceMessage(filePath: String, messageId: Long) {
        if (viewModel.voiceRecorder.isPlaying) {
            viewModel.voiceRecorder.stopPlayback()
        } else {
            viewModel.voiceRecorder.playVoice(
                filePath = filePath,
                onComplete = { runOnUiThread { messageAdapter.notifyDataSetChanged() } },
                onError = { error -> runOnUiThread { showToast("Playback error: $error") } }
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_disconnect -> {
                viewModel.disconnect()
                finish()
                true
            }
            R.id.action_listen -> {
                viewModel.startServer()
                showToast("Waiting for incoming connection...")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) viewModel.voiceRecorder.cancelRecording()
    }
}
