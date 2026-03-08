package com.rehaan.bluetoothchat.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Context
import com.rehaan.bluetoothchat.utils.Constants
import timber.log.Timber

class BluetoothServer(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val onConnectionAccepted: (BluetoothSocket) -> Unit,
    private val onError: (String) -> Unit
) : Thread() {

    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var isRunning = false

    init {
        name = "BluetoothServerThread"
        serverSocket = createServerSocket()
    }

    private fun createServerSocket(): BluetoothServerSocket? {
        if (!hasConnectPermission()) {
            onError("Missing BLUETOOTH_CONNECT permission")
            return null
        }
        return try {
            bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                Constants.SERVICE_NAME,
                Constants.APP_UUID
            )
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException creating server socket")
            onError("Permission denied: ${e.message}")
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to create server socket")
            onError("Failed to start server: ${e.message}")
            null
        }
    }

    override fun run() {
        isRunning = true
        Timber.d("BluetoothServer: Listening for connections...")

        while (isRunning) {
            val socket: BluetoothSocket? = try {
                serverSocket?.accept()
            } catch (e: Exception) {
                if (isRunning) {
                    Timber.e(e, "BluetoothServer: accept() failed")
                }
                null
            }

            socket?.let {
                Timber.d("BluetoothServer: Connection accepted from ${getDeviceName(it)}")
                onConnectionAccepted(it)
                // After accepting one connection, stop listening
                cancel()
            }
        }
        Timber.d("BluetoothServer: Thread ended")
    }

    fun cancel() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Timber.e(e, "BluetoothServer: Failed to close server socket")
        }
    }

    private fun getDeviceName(socket: BluetoothSocket): String {
        return try {
            if (hasConnectPermission()) socket.remoteDevice.name ?: "Unknown"
            else "Unknown"
        } catch (e: SecurityException) {
            "Unknown"
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }
}
