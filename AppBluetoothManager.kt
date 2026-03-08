package com.rehaan.bluetoothchat.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.rehaan.bluetoothchat.domain.model.DeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val pairedDevices: StateFlow<List<DeviceInfo>> = _pairedDevices.asStateFlow()

    private val discoveredDeviceList = mutableListOf<DeviceInfo>()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    _isBluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
                    Timber.d("Bluetooth state changed: $state")
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { addDiscoveredDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isDiscovering.value = true
                    discoveredDeviceList.clear()
                    _discoveredDevices.value = emptyList()
                    Timber.d("Discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isDiscovering.value = false
                    Timber.d("Discovery finished. Found ${discoveredDeviceList.size} devices")
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    refreshPairedDevices()
                }
            }
        }
    }

    init {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
        registerReceivers()
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothStateReceiver, filter)
    }

    fun unregisterReceivers() {
        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: IllegalArgumentException) {
            Timber.w("Receiver not registered")
        }
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun enableBluetooth(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }

    fun makeDiscoverable(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
    }

    fun startDiscovery(): Boolean {
        if (!hasBluetoothScanPermission()) {
            Timber.w("Missing BLUETOOTH_SCAN permission")
            return false
        }
        if (!isBluetoothEnabled()) return false
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        discoveredDeviceList.clear()
        _discoveredDevices.value = emptyList()
        return bluetoothAdapter?.startDiscovery() == true
    }

    fun stopDiscovery() {
        if (!hasBluetoothScanPermission()) return
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
    }

    fun refreshPairedDevices() {
        if (!hasBluetoothConnectPermission()) {
            _pairedDevices.value = emptyList()
            return
        }
        val paired = bluetoothAdapter?.bondedDevices?.map { device ->
            DeviceInfo(
                name = getDeviceName(device),
                address = device.address,
                isPaired = true
            )
        } ?: emptyList()
        _pairedDevices.value = paired
        Timber.d("Paired devices refreshed: ${paired.size}")
    }

    fun getBluetoothDevice(address: String): BluetoothDevice? {
        return try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid device address: $address")
            null
        }
    }

    private fun addDiscoveredDevice(device: BluetoothDevice) {
        if (!hasBluetoothConnectPermission()) return
        val address = device.address ?: return
        if (discoveredDeviceList.none { it.address == address }) {
            val info = DeviceInfo(
                name = getDeviceName(device),
                address = address,
                isPaired = device.bondState == BluetoothDevice.BOND_BONDED
            )
            discoveredDeviceList.add(info)
            _discoveredDevices.value = discoveredDeviceList.toList()
        }
    }

    private fun getDeviceName(device: BluetoothDevice): String {
        return try {
            if (hasBluetoothConnectPermission()) {
                device.name ?: "Unknown Device"
            } else {
                "Unknown Device"
            }
        } catch (e: SecurityException) {
            "Unknown Device"
        }
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun getLocalDeviceName(): String {
        return try {
            if (hasBluetoothConnectPermission()) {
                bluetoothAdapter?.name ?: "This Device"
            } else "This Device"
        } catch (e: SecurityException) {
            "This Device"
        }
    }

    fun getLocalDeviceAddress(): String {
        return try {
            if (hasBluetoothConnectPermission()) {
                bluetoothAdapter?.address ?: "00:00:00:00:00:00"
            } else "00:00:00:00:00:00"
        } catch (e: SecurityException) {
            "00:00:00:00:00:00"
        }
    }
}
