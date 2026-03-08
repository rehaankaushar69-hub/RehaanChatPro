package com.rehaan.bluetoothchat.ui.devices

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.rehaan.bluetoothchat.R
import com.rehaan.bluetoothchat.adapters.DeviceAdapter
import com.rehaan.bluetoothchat.databinding.ActivityDeviceListBinding
import com.rehaan.bluetoothchat.domain.model.DeviceInfo
import com.rehaan.bluetoothchat.ui.chat.ChatActivity
import com.rehaan.bluetoothchat.utils.Constants
import com.rehaan.bluetoothchat.utils.gone
import com.rehaan.bluetoothchat.utils.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DeviceListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceListBinding
    private val viewModel: DeviceListViewModel by viewModels()

    private lateinit var pairedAdapter: DeviceAdapter
    private lateinit var discoveredAdapter: DeviceAdapter

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshPairedDevices()
        } else {
            Snackbar.make(binding.root, "Bluetooth is required", Snackbar.LENGTH_LONG).show()
        }
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Find Devices"

        setupAdapters()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupAdapters() {
        pairedAdapter = DeviceAdapter { device -> onDeviceClicked(device) }
        discoveredAdapter = DeviceAdapter { device -> onDeviceClicked(device) }

        binding.rvPairedDevices.adapter = pairedAdapter
        binding.rvDiscoveredDevices.adapter = discoveredAdapter
    }

    private fun setupClickListeners() {
        binding.btnScan.setOnClickListener {
            if (viewModel.isDiscovering.value) {
                viewModel.stopDiscovery()
                binding.btnScan.text = "Scan for Devices"
            } else {
                startDiscovery()
            }
        }

        binding.btnMakeDiscoverable.setOnClickListener {
            val intent = viewModel.bluetoothManager.makeDiscoverable()
            discoverableLauncher.launch(intent)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.isBluetoothEnabled.collect { enabled ->
                if (!enabled) {
                    val intent = viewModel.bluetoothManager.enableBluetooth()
                    enableBluetoothLauncher.launch(intent)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.pairedDevices.collect { devices ->
                pairedAdapter.submitList(devices)
                binding.tvNoPairedDevices.visibility =
                    if (devices.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.discoveredDevices.collect { devices ->
                discoveredAdapter.submitList(devices)
                binding.tvNoDiscoveredDevices.visibility =
                    if (devices.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.isDiscovering.collect { discovering ->
                if (discovering) {
                    binding.progressDiscovery.visible()
                    binding.btnScan.text = "Stop Scanning"
                    binding.tvDiscoveryStatus.text = "Scanning for devices..."
                } else {
                    binding.progressDiscovery.gone()
                    binding.btnScan.text = "Scan for Devices"
                    binding.tvDiscoveryStatus.text = "Tap 'Scan' to find nearby devices"
                }
            }
        }
    }

    private fun startDiscovery() {
        val started = viewModel.startDiscovery()
        if (!started) {
            Snackbar.make(binding.root, "Could not start device scan", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun onDeviceClicked(device: DeviceInfo) {
        viewModel.stopDiscovery()
        viewModel.onDeviceSelected(device)

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(Constants.EXTRA_DEVICE_ADDRESS, device.address)
            putExtra(Constants.EXTRA_DEVICE_NAME, device.name)
        }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_device_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_refresh -> { viewModel.refreshPairedDevices(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopDiscovery()
    }
}
