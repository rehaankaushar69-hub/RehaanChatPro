package com.rehaan.bluetoothchat.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rehaan.bluetoothchat.R
import com.rehaan.bluetoothchat.ui.main.MainActivity
import com.rehaan.bluetoothchat.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothService : Service() {

    @Inject lateinit var bluetoothManager: AppBluetoothManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val binder = LocalBinder()

    private var serverThread: BluetoothServer? = null
    private var clientThread: BluetoothClient? = null
    private var connectionThread: BluetoothConnectionThread? = null

    private val _connectionState = MutableStateFlow(Constants.STATE_NONE)
    val connectionState: StateFlow<Int> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow("")
    val connectedDeviceName: StateFlow<String> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow("")
    val connectedDeviceAddress: StateFlow<String> = _connectedDeviceAddress.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<BluetoothMessage>()
    val incomingMessages: SharedFlow<BluetoothMessage> = _incomingMessages.asSharedFlow()

    private val _fileTransferProgress = MutableSharedFlow<Pair<Int, String>>()
    val fileTransferProgress: SharedFlow<Pair<Int, String>> = _fileTransferProgress.asSharedFlow()

    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(Constants.NOTIFICATION_ID, buildNotification())
        log("Service created")
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllThreads()
        serviceJob.cancel()
        log("Service destroyed")
    }

    fun startServer() {
        stopAllThreads()
        setState(Constants.STATE_LISTEN)
        log("Starting server mode...")

        val adapter = bluetoothManager.bluetoothAdapter ?: run {
            log("ERROR: Bluetooth adapter unavailable")
            return
        }

        serverThread = BluetoothServer(
            context = applicationContext,
            bluetoothAdapter = adapter,
            onConnectionAccepted = { socket -> handleConnectedSocket(socket, true) },
            onError = { error -> log("Server error: $error") }
        )
        serverThread?.start()
    }

    fun connectToDevice(deviceAddress: String, deviceName: String) {
        stopAllThreads()
        setState(Constants.STATE_CONNECTING)
        log("Connecting to $deviceName ($deviceAddress)...")

        val adapter = bluetoothManager.bluetoothAdapter ?: run {
            log("ERROR: Bluetooth adapter unavailable")
            setState(Constants.STATE_NONE)
            return
        }

        val device = bluetoothManager.getBluetoothDevice(deviceAddress) ?: run {
            log("ERROR: Device not found for address $deviceAddress")
            setState(Constants.STATE_NONE)
            return
        }

        _connectedDeviceName.value = deviceName
        _connectedDeviceAddress.value = deviceAddress

        clientThread = BluetoothClient(
            context = applicationContext,
            bluetoothAdapter = adapter,
            device = device,
            onConnected = { socket -> handleConnectedSocket(socket, false) },
            onError = { error ->
                log("Client error: $error")
                setState(Constants.STATE_NONE)
            }
        )
        clientThread?.start()
    }

    private fun handleConnectedSocket(socket: BluetoothSocket, isServer: Boolean) {
        log("Socket connected. Mode: ${if (isServer) "Server" else "Client"}")

        // Stop existing connection thread if any
        connectionThread?.cancel()

        // Try to get device name from socket
        try {
            val device = socket.remoteDevice
            val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
            _connectedDeviceName.value = name
            _connectedDeviceAddress.value = device.address
        } catch (e: Exception) {
            Timber.w(e, "Could not read remote device info from socket")
        }

        connectionThread = BluetoothConnectionThread(
            socket = socket,
            onMessageReceived = { data -> handleIncomingData(data) },
            onFileProgress = { progress, name ->
                serviceScope.launch {
                    _fileTransferProgress.emit(Pair(progress, name))
                }
            },
            onDisconnected = {
                log("Connection lost with ${_connectedDeviceName.value}")
                setState(Constants.STATE_NONE)
                _connectedDeviceName.value = ""
                _connectedDeviceAddress.value = ""
                // Restart server to listen for new connections
                startServer()
            },
            onError = { error -> log("Connection error: $error") }
        )
        connectionThread?.start()
        setState(Constants.STATE_CONNECTED)
        log("Connected to ${_connectedDeviceName.value}")
    }

    private fun handleIncomingData(data: ByteArray) {
        serviceScope.launch {
            val header = extractHeader(data)
            val message = when {
                header.startsWith(Constants.PROTOCOL_FILE_DATA) -> {
                    val nullIdx = data.indexOf(0.toByte())
                    val fileData = if (nullIdx >= 0 && nullIdx < data.size - 1)
                        data.sliceArray(nullIdx + 1 until data.size)
                    else byteArrayOf()
                    val meta = header.removePrefix(Constants.PROTOCOL_FILE_DATA).split("|")
                    val fileName = meta.getOrElse(0) { "file" }
                    val mimeType = meta.getOrElse(2) { "application/octet-stream" }
                    val savedFile = saveIncomingFile(fileName, fileData)
                    BluetoothMessage.FileMessage(
                        fileName = fileName,
                        mimeType = mimeType,
                        fileSize = fileData.size.toLong(),
                        filePath = savedFile?.absolutePath ?: ""
                    )
                }
                header.startsWith(Constants.PROTOCOL_VOICE_DATA) -> {
                    val nullIdx = data.indexOf(0.toByte())
                    val voiceData = if (nullIdx >= 0 && nullIdx < data.size - 1)
                        data.sliceArray(nullIdx + 1 until data.size)
                    else byteArrayOf()
                    val meta = header.removePrefix(Constants.PROTOCOL_VOICE_DATA).split("|")
                    val fileName = meta.getOrElse(0) { "voice.3gp" }
                    val duration = meta.getOrElse(1) { "0" }.toIntOrNull() ?: 0
                    val savedFile = saveIncomingFile(fileName, voiceData)
                    BluetoothMessage.VoiceMessage(
                        fileName = fileName,
                        duration = duration,
                        filePath = savedFile?.absolutePath ?: ""
                    )
                }
                else -> {
                    BluetoothMessage.TextMessage(String(data, Charsets.UTF_8))
                }
            }
            _incomingMessages.emit(message)
            log("Message received: ${message::class.simpleName}")
        }
    }

    private fun extractHeader(data: ByteArray): String {
        val nullIdx = data.indexOf(0.toByte())
        val headerBytes = if (nullIdx >= 0) data.sliceArray(0 until nullIdx) else data
        return String(headerBytes, Charsets.UTF_8)
    }

    private fun saveIncomingFile(fileName: String, data: ByteArray): File? {
        return try {
            val dir = File(filesDir, "received")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${System.currentTimeMillis()}_$fileName")
            file.writeBytes(data)
            file
        } catch (e: Exception) {
            Timber.e(e, "Failed to save incoming file")
            null
        }
    }

    fun sendTextMessage(text: String) {
        val thread = connectionThread ?: run {
            log("ERROR: Not connected")
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            thread.sendText(text.toByteArray(Charsets.UTF_8))
            log("Text sent: ${text.take(50)}")
        }
    }

    fun sendFile(fileName: String, mimeType: String, data: ByteArray) {
        val thread = connectionThread ?: run {
            log("ERROR: Not connected")
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            thread.sendFile(fileName, mimeType, data) { progress ->
                serviceScope.launch {
                    _fileTransferProgress.emit(Pair(progress, fileName))
                }
            }
            log("File sent: $fileName")
        }
    }

    fun sendVoiceMessage(fileName: String, duration: Int, data: ByteArray) {
        val thread = connectionThread ?: run {
            log("ERROR: Not connected")
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            thread.sendVoice(fileName, duration, data)
            log("Voice sent: $fileName ($duration sec)")
        }
    }

    fun disconnect() {
        stopAllThreads()
        setState(Constants.STATE_NONE)
        _connectedDeviceName.value = ""
        _connectedDeviceAddress.value = ""
        log("Disconnected")
        startServer()
    }

    private fun stopAllThreads() {
        serverThread?.cancel()
        serverThread = null
        clientThread?.cancel()
        clientThread = null
        connectionThread?.cancel()
        connectionThread = null
    }

    private fun setState(state: Int) {
        _connectionState.value = state
        val stateStr = when (state) {
            Constants.STATE_NONE -> "NONE"
            Constants.STATE_LISTEN -> "LISTENING"
            Constants.STATE_CONNECTING -> "CONNECTING"
            Constants.STATE_CONNECTED -> "CONNECTED"
            else -> "UNKNOWN"
        }
        log("State changed: $stateStr")
    }

    private fun log(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val entry = "[$timestamp] $msg"
        Timber.d(entry)
        val current = _debugLogs.value.toMutableList()
        current.add(entry)
        if (current.size > 200) current.removeAt(0)
        _debugLogs.value = current
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("BlueChat")
            .setContentText("Bluetooth service is running")
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}

sealed class BluetoothMessage {
    data class TextMessage(val text: String) : BluetoothMessage()
    data class FileMessage(
        val fileName: String,
        val mimeType: String,
        val fileSize: Long,
        val filePath: String
    ) : BluetoothMessage()
    data class VoiceMessage(
        val fileName: String,
        val duration: Int,
        val filePath: String
    ) : BluetoothMessage()
}
