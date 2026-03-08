package com.rehaan.bluetoothchat.bluetooth

import android.bluetooth.BluetoothSocket
import com.rehaan.bluetoothchat.utils.Constants
import timber.log.Timber
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class BluetoothConnectionThread(
    private val socket: BluetoothSocket,
    private val onMessageReceived: (ByteArray) -> Unit,
    private val onFileProgress: (Int, String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onError: (String) -> Unit
) : Thread() {

    private val inputStream: DataInputStream
    private val outputStream: DataOutputStream
    @Volatile private var isRunning = false

    init {
        name = "BluetoothConnectionThread"
        var tmpIn: DataInputStream? = null
        var tmpOut: DataOutputStream? = null
        try {
            tmpIn = DataInputStream(socket.inputStream)
            tmpOut = DataOutputStream(socket.outputStream)
        } catch (e: IOException) {
            Timber.e(e, "ConnectionThread: Error getting streams")
        }
        inputStream = tmpIn!!
        outputStream = tmpOut!!
    }

    override fun run() {
        isRunning = true
        Timber.d("ConnectionThread: Started, ready to receive data")

        while (isRunning) {
            try {
                // Read the packet type first (1 byte)
                val packetType = inputStream.readByte().toInt()

                when (packetType) {
                    PACKET_TEXT -> receiveTextPacket()
                    PACKET_FILE -> receiveFilePacket()
                    PACKET_VOICE -> receiveVoicePacket()
                    else -> {
                        Timber.w("ConnectionThread: Unknown packet type: $packetType")
                    }
                }
            } catch (e: IOException) {
                if (isRunning) {
                    Timber.e(e, "ConnectionThread: Stream read error")
                    onDisconnected()
                }
                break
            } catch (e: Exception) {
                if (isRunning) {
                    Timber.e(e, "ConnectionThread: Unexpected error")
                    onError(e.message ?: "Unknown error")
                }
                break
            }
        }

        Timber.d("ConnectionThread: Ended")
    }

    private fun receiveTextPacket() {
        val length = inputStream.readInt()
        val data = ByteArray(length)
        inputStream.readFully(data)
        onMessageReceived(data)
    }

    private fun receiveFilePacket() {
        // Read metadata
        val metaLength = inputStream.readInt()
        val metaBytes = ByteArray(metaLength)
        inputStream.readFully(metaBytes)
        val meta = String(metaBytes, Charsets.UTF_8)
        val parts = meta.split("|")
        val fileName = parts.getOrElse(0) { "unknown" }
        val fileSize = parts.getOrElse(1) { "0" }.toLongOrNull() ?: 0L
        val mimeType = parts.getOrElse(2) { "application/octet-stream" }

        // Read file data
        val buffer = ByteArray(Constants.BUFFER_SIZE)
        val allData = mutableListOf<Byte>()
        var totalRead = 0L

        while (totalRead < fileSize) {
            val toRead = minOf(Constants.BUFFER_SIZE.toLong(), fileSize - totalRead).toInt()
            val bytesRead = inputStream.read(buffer, 0, toRead)
            if (bytesRead == -1) break
            for (i in 0 until bytesRead) allData.add(buffer[i])
            totalRead += bytesRead
            val progress = ((totalRead.toFloat() / fileSize) * 100).toInt()
            onFileProgress(progress, fileName)
        }

        // Package as a special message with file data
        val fileDataMsg = buildFileMessage(fileName, fileSize, mimeType, allData.toByteArray())
        onMessageReceived(fileDataMsg)
    }

    private fun receiveVoicePacket() {
        val metaLength = inputStream.readInt()
        val metaBytes = ByteArray(metaLength)
        inputStream.readFully(metaBytes)
        val meta = String(metaBytes, Charsets.UTF_8)
        val parts = meta.split("|")
        val fileName = parts.getOrElse(0) { "voice.3gp" }
        val duration = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
        val fileSize = parts.getOrElse(2) { "0" }.toLongOrNull() ?: 0L

        val buffer = ByteArray(Constants.BUFFER_SIZE)
        val allData = mutableListOf<Byte>()
        var totalRead = 0L

        while (totalRead < fileSize) {
            val toRead = minOf(Constants.BUFFER_SIZE.toLong(), fileSize - totalRead).toInt()
            val bytesRead = inputStream.read(buffer, 0, toRead)
            if (bytesRead == -1) break
            for (i in 0 until bytesRead) allData.add(buffer[i])
            totalRead += bytesRead
        }

        val voiceMsg = buildVoiceMessage(fileName, duration, fileSize, allData.toByteArray())
        onMessageReceived(voiceMsg)
    }

    @Synchronized
    fun sendText(data: ByteArray) {
        try {
            outputStream.writeByte(PACKET_TEXT)
            outputStream.writeInt(data.size)
            outputStream.write(data)
            outputStream.flush()
            Timber.d("ConnectionThread: Text sent (${data.size} bytes)")
        } catch (e: IOException) {
            Timber.e(e, "ConnectionThread: Failed to send text")
            onError("Failed to send message: ${e.message}")
        }
    }

    @Synchronized
    fun sendFile(
        fileName: String,
        mimeType: String,
        data: ByteArray,
        onProgress: (Int) -> Unit
    ) {
        try {
            val meta = "$fileName|${data.size}|$mimeType"
            val metaBytes = meta.toByteArray(Charsets.UTF_8)

            outputStream.writeByte(PACKET_FILE)
            outputStream.writeInt(metaBytes.size)
            outputStream.write(metaBytes)

            var offset = 0
            while (offset < data.size) {
                val chunk = minOf(Constants.BUFFER_SIZE, data.size - offset)
                outputStream.write(data, offset, chunk)
                offset += chunk
                val progress = ((offset.toFloat() / data.size) * 100).toInt()
                onProgress(progress)
            }
            outputStream.flush()
            Timber.d("ConnectionThread: File sent ($fileName, ${data.size} bytes)")
        } catch (e: IOException) {
            Timber.e(e, "ConnectionThread: Failed to send file")
            onError("Failed to send file: ${e.message}")
        }
    }

    @Synchronized
    fun sendVoice(fileName: String, duration: Int, data: ByteArray) {
        try {
            val meta = "$fileName|$duration|${data.size}"
            val metaBytes = meta.toByteArray(Charsets.UTF_8)

            outputStream.writeByte(PACKET_VOICE)
            outputStream.writeInt(metaBytes.size)
            outputStream.write(metaBytes)
            outputStream.write(data)
            outputStream.flush()
            Timber.d("ConnectionThread: Voice sent ($fileName, ${data.size} bytes)")
        } catch (e: IOException) {
            Timber.e(e, "ConnectionThread: Failed to send voice")
            onError("Failed to send voice: ${e.message}")
        }
    }

    fun cancel() {
        isRunning = false
        try {
            socket.close()
        } catch (e: IOException) {
            Timber.e(e, "ConnectionThread: Failed to close socket")
        }
    }

    private fun buildFileMessage(
        fileName: String,
        fileSize: Long,
        mimeType: String,
        data: ByteArray
    ): ByteArray {
        val header = "${Constants.PROTOCOL_FILE_DATA}$fileName|$fileSize|$mimeType"
        val headerBytes = header.toByteArray(Charsets.UTF_8)
        return headerBytes + byteArrayOf(0) + data  // null separator between header and data
    }

    private fun buildVoiceMessage(
        fileName: String,
        duration: Int,
        fileSize: Long,
        data: ByteArray
    ): ByteArray {
        val header = "${Constants.PROTOCOL_VOICE_DATA}$fileName|$duration|$fileSize"
        val headerBytes = header.toByteArray(Charsets.UTF_8)
        return headerBytes + byteArrayOf(0) + data
    }

    companion object {
        const val PACKET_TEXT = 0x01
        const val PACKET_FILE = 0x02
        const val PACKET_VOICE = 0x03
    }
}
