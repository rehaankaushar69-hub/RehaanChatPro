package com.rehaan.bluetoothchat.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Context
import com.rehaan.bluetoothchat.utils.Constants
import timber.log.Timber
import java.io.IOException

class BluetoothClient(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val onConnected: (BluetoothSocket) -> Unit,
    private val onError: (String) -> Unit
) : Thread() {

    private var socket: BluetoothSocket? = null
    @Volatile private var isRunning = false

    init {
        name = "BluetoothClientThread"
        socket = createSocket()
    }

    private fun createSocket(): BluetoothSocket? {
        if (!hasConnectPermission()) {
            onError("Missing BLUETOOTH_CONNECT permission")
            return null
        }
        return try {
            device.createRfcommSocketToServiceRecord(Constants.APP_UUID)
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException creating client socket")
            onError("Permission denied: ${e.message}")
            null
        } catch (e: IOException) {
            Timber.e(e, "IOException creating client socket")
            try {
                // Fallback: create insecure socket
                device.createInsecureRfcommSocketToServiceRecord(Constants.APP_UUID)
            } catch (e2: Exception) {
                onError("Failed to create socket: ${e2.message}")
                null
            }
        }
    }

    override fun run() {
        isRunning = true

        // Always cancel discovery before connecting
        try {
            if (hasConnectPermission()) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Timber.w(e, "Failed to cancel discovery")
        }

        val clientSocket = socket
        if (clientSocket == null) {
            onError("Socket not initialized")
            return
        }

        Timber.d("BluetoothClient: Attempting connection to ${getDeviceName()}")

        try {
            clientSocket.connect()
            Timber.d("BluetoothClient: Connected successfully to ${getDeviceName()}")
            onConnected(clientSocket)
        } catch (e: SecurityException) {
            Timber.e(e, "BluetoothClient: SecurityException during connect")
            closeSocket()
            onError("Permission denied during connection: ${e.message}")
        } catch (e: IOException) {
            Timber.e(e, "BluetoothClient: IOException during connect")
            closeSocket()
            if (isRunning) {
                onError("Connection failed: ${e.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "BluetoothClient: Unexpected error during connect")
            closeSocket()
            onError("Unexpected error: ${e.message}")
        }
    }

    fun cancel() {
        isRunning = false
        closeSocket()
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Timber.e(e, "BluetoothClient: Failed to close socket")
        }
    }

    private fun getDeviceName(): String {
        return try {
            if (hasConnectPermission()) device.name ?: device.address
            else device.address
        } catch (e: SecurityException) {
            device.address
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
