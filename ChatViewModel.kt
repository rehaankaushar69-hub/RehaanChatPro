package com.rehaan.bluetoothchat.ui.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rehaan.bluetoothchat.bluetooth.BluetoothMessage
import com.rehaan.bluetoothchat.bluetooth.BluetoothService
import com.rehaan.bluetoothchat.bluetooth.VoiceRecorder
import com.rehaan.bluetoothchat.data.repository.ChatRepository
import com.rehaan.bluetoothchat.domain.model.Message
import com.rehaan.bluetoothchat.domain.model.MessageDirection
import com.rehaan.bluetoothchat.domain.model.MessageType
import com.rehaan.bluetoothchat.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private var bluetoothService: BluetoothService? = null
    private var isBound = false

    lateinit var voiceRecorder: VoiceRecorder

    private val _chatId = MutableStateFlow("")
    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _deviceAddress = MutableStateFlow("")
    val deviceAddress: StateFlow<String> = _deviceAddress.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _connectionState = MutableStateFlow(Constants.STATE_NONE)
    val connectionState: StateFlow<Int> = _connectionState.asStateFlow()

    private val _fileTransferProgress = MutableSharedFlow<Pair<Int, String>>()
    val fileTransferProgress: SharedFlow<Pair<Int, String>> = _fileTransferProgress.asSharedFlow()

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()

    val isConnected: Boolean get() = _connectionState.value == Constants.STATE_CONNECTED

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as BluetoothService.LocalBinder
            bluetoothService = localBinder.getService()
            isBound = true
            observeServiceFlows()
            initiateConnection()
            Timber.d("ChatViewModel: Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bluetoothService = null
            isBound = false
            Timber.d("ChatViewModel: Service disconnected")
        }
    }

    fun initialize(deviceAddress: String, deviceName: String) {
        _deviceAddress.value = deviceAddress
        _deviceName.value = deviceName
        _chatId.value = ChatRepository.generateChatId(deviceAddress)
        voiceRecorder = VoiceRecorder(context)

        loadMessages()
        bindService()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            chatRepository.getMessagesForChat(_chatId.value).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    private fun bindService() {
        Intent(context, BluetoothService::class.java).also { intent ->
            context.startService(intent)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun initiateConnection() {
        val service = bluetoothService ?: return
        val state = service.connectionState.value
        if (state != Constants.STATE_CONNECTED) {
            service.connectToDevice(_deviceAddress.value, _deviceName.value)
        }
    }

    private fun observeServiceFlows() {
        val service = bluetoothService ?: return

        viewModelScope.launch {
            service.connectionState.collect { state ->
                _connectionState.value = state
            }
        }

        viewModelScope.launch {
            service.incomingMessages.collect { bluetoothMessage ->
                handleIncomingMessage(bluetoothMessage)
            }
        }

        viewModelScope.launch {
            service.fileTransferProgress.collect { progress ->
                _fileTransferProgress.emit(progress)
            }
        }
    }

    private fun handleIncomingMessage(msg: BluetoothMessage) {
        viewModelScope.launch {
            val message = when (msg) {
                is BluetoothMessage.TextMessage -> Message(
                    chatId = _chatId.value,
                    content = msg.text,
                    type = MessageType.TEXT,
                    direction = MessageDirection.RECEIVED,
                    timestamp = System.currentTimeMillis()
                )
                is BluetoothMessage.FileMessage -> Message(
                    chatId = _chatId.value,
                    content = msg.fileName,
                    type = if (msg.mimeType.startsWith("image")) MessageType.IMAGE else MessageType.FILE,
                    direction = MessageDirection.RECEIVED,
                    timestamp = System.currentTimeMillis(),
                    filePath = msg.filePath,
                    fileName = msg.fileName,
                    fileSize = msg.fileSize,
                    mimeType = msg.mimeType
                )
                is BluetoothMessage.VoiceMessage -> Message(
                    chatId = _chatId.value,
                    content = "Voice message",
                    type = MessageType.VOICE,
                    direction = MessageDirection.RECEIVED,
                    timestamp = System.currentTimeMillis(),
                    filePath = msg.filePath,
                    fileName = msg.fileName,
                    duration = msg.duration
                )
            }
            chatRepository.insertMessage(message)
        }
    }

    fun sendTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val service = bluetoothService
        if (service == null || !isConnected) {
            viewModelScope.launch { _error.emit("Not connected to device") }
            return
        }

        viewModelScope.launch {
            val message = Message(
                chatId = _chatId.value,
                content = trimmed,
                type = MessageType.TEXT,
                direction = MessageDirection.SENT,
                timestamp = System.currentTimeMillis(),
                isDelivered = true
            )
            chatRepository.insertMessage(message)
            service.sendTextMessage(trimmed)
        }
    }

    fun sendFile(fileName: String, mimeType: String, data: ByteArray) {
        val service = bluetoothService
        if (service == null || !isConnected) {
            viewModelScope.launch { _error.emit("Not connected to device") }
            return
        }

        viewModelScope.launch {
            val type = when {
                mimeType.startsWith("image") -> MessageType.IMAGE
                mimeType.startsWith("audio") -> MessageType.VOICE
                else -> MessageType.FILE
            }
            val message = Message(
                chatId = _chatId.value,
                content = fileName,
                type = type,
                direction = MessageDirection.SENT,
                timestamp = System.currentTimeMillis(),
                fileName = fileName,
                fileSize = data.size.toLong(),
                mimeType = mimeType
            )
            chatRepository.insertMessage(message)
            service.sendFile(fileName, mimeType, data)
        }
    }

    fun sendVoiceMessage(filePath: String, duration: Int) {
        val service = bluetoothService
        if (service == null || !isConnected) {
            viewModelScope.launch { _error.emit("Not connected to device") }
            return
        }

        viewModelScope.launch {
            try {
                val file = java.io.File(filePath)
                val data = file.readBytes()
                val fileName = file.name

                val message = Message(
                    chatId = _chatId.value,
                    content = "Voice message (${duration}s)",
                    type = MessageType.VOICE,
                    direction = MessageDirection.SENT,
                    timestamp = System.currentTimeMillis(),
                    filePath = filePath,
                    fileName = fileName,
                    fileSize = data.size.toLong(),
                    duration = duration
                )
                chatRepository.insertMessage(message)
                service.sendVoiceMessage(fileName, duration, data)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send voice message")
                _error.emit("Failed to send voice message")
            }
        }
    }

    fun markMessagesAsRead() {
        viewModelScope.launch {
            chatRepository.markMessagesAsRead(_chatId.value)
        }
    }

    fun disconnect() {
        bluetoothService?.disconnect()
    }

    fun startServer() {
        bluetoothService?.startServer()
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.release()
        if (isBound) {
            try { context.unbindService(serviceConnection) } catch (e: Exception) { }
            isBound = false
        }
    }
}
