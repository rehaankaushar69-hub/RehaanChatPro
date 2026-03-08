package com.rehaan.bluetoothchat.ui.debug

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rehaan.bluetoothchat.bluetooth.AppBluetoothManager
import com.rehaan.bluetoothchat.bluetooth.BluetoothService
import com.rehaan.bluetoothchat.databinding.ActivityDebugBinding
import com.rehaan.bluetoothchat.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DebugActivity : AppCompatActivity() {

    @Inject lateinit var bluetoothManager: AppBluetoothManager

    private lateinit var binding: ActivityDebugBinding
    private var bluetoothService: BluetoothService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as BluetoothService.LocalBinder
            bluetoothService = localBinder.getService()
            isBound = true
            observeServiceLogs()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bluetoothService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Debug Panel"

        updateDeviceInfo()
        bindBluetoothService()
        setupButtons()
    }

    private fun updateDeviceInfo() {
        binding.tvDeviceName.text = "Device Name: ${bluetoothManager.getLocalDeviceName()}"
        binding.tvDeviceAddress.text = "Device Address: ${bluetoothManager.getLocalDeviceAddress()}"
        binding.tvBluetoothState.text = "Bluetooth State: ${if (bluetoothManager.isBluetoothEnabled()) "ENABLED" else "DISABLED"}"
        binding.tvBluetoothSupported.text = "Bluetooth Supported: ${bluetoothManager.isBluetoothSupported()}"
    }

    private fun bindBluetoothService() {
        Intent(this, BluetoothService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeServiceLogs() {
        val service = bluetoothService ?: return

        lifecycleScope.launch {
            service.connectionState.collect { state ->
                val stateStr = when (state) {
                    Constants.STATE_NONE -> "NONE"
                    Constants.STATE_LISTEN -> "LISTENING"
                    Constants.STATE_CONNECTING -> "CONNECTING"
                    Constants.STATE_CONNECTED -> "CONNECTED"
                    else -> "UNKNOWN ($state)"
                }
                binding.tvConnectionState.text = "Connection State: $stateStr"
            }
        }

        lifecycleScope.launch {
            service.connectedDeviceName.collect { name ->
                binding.tvConnectedDevice.text = "Connected Device: ${if (name.isEmpty()) "None" else name}"
            }
        }

        lifecycleScope.launch {
            service.debugLogs.collect { logs ->
                val text = logs.takeLast(100).joinToString("\n")
                binding.tvLogs.text = text
                binding.scrollLogs.post {
                    binding.scrollLogs.fullScroll(android.view.View.FOCUS_DOWN)
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnRefresh.setOnClickListener {
            updateDeviceInfo()
        }

        binding.btnClearLogs.setOnClickListener {
            binding.tvLogs.text = ""
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try { unbindService(serviceConnection) } catch (e: Exception) { }
        }
    }
}
