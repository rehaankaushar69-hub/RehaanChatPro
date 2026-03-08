package com.rehaan.bluetoothchat.ui.main

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.rehaan.bluetoothchat.R
import com.rehaan.bluetoothchat.adapters.ChatSessionAdapter
import com.rehaan.bluetoothchat.databinding.ActivityMainBinding
import com.rehaan.bluetoothchat.ui.chat.ChatActivity
import com.rehaan.bluetoothchat.ui.debug.DebugActivity
import com.rehaan.bluetoothchat.ui.devices.DeviceListActivity
import com.rehaan.bluetoothchat.utils.Constants
import com.rehaan.bluetoothchat.utils.PermissionHelper
import com.rehaan.bluetoothchat.utils.gone
import com.rehaan.bluetoothchat.utils.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var chatSessionAdapter: ChatSessionAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Snackbar.make(
                binding.root,
                "Some permissions were denied. Bluetooth features may be limited.",
                Snackbar.LENGTH_LONG
            ).show()
        } else {
            viewModel.bluetoothManager.refreshPairedDevices()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.bluetoothManager.refreshPairedDevices()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        if (!viewModel.bluetoothManager.isBluetoothSupported()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Bluetooth Not Supported")
                .setMessage("This device does not support Bluetooth. BlueChat requires Bluetooth to function.")
                .setPositiveButton("Exit") { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }

        setupRecyclerView()
        setupFab()
        observeViewModel()
        requestPermissions()
    }

    private fun setupRecyclerView() {
        chatSessionAdapter = ChatSessionAdapter(
            onSessionClicked = { session ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra(Constants.EXTRA_DEVICE_ADDRESS, session.deviceAddress)
                    putExtra(Constants.EXTRA_DEVICE_NAME, session.deviceName)
                }
                startActivity(intent)
            },
            onSessionLongClicked = { session ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Chat")
                    .setMessage("Delete chat with ${session.deviceName}?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteSession(session.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvChats.adapter = chatSessionAdapter
    }

    private fun setupFab() {
        binding.fabNewChat.setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.chatSessions.collect { sessions ->
                chatSessionAdapter.submitList(sessions)
                if (sessions.isEmpty()) {
                    binding.tvEmptyState.visible()
                    binding.rvChats.gone()
                } else {
                    binding.tvEmptyState.gone()
                    binding.rvChats.visible()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isBluetoothEnabled.collect { enabled ->
                if (!enabled) {
                    val intent = viewModel.bluetoothManager.enableBluetooth()
                    enableBluetoothLauncher.launch(intent)
                }
            }
        }
    }

    private fun requestPermissions() {
        val missing = PermissionHelper.getMissingPermissions(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_scan -> {
                startActivity(Intent(this, DeviceListActivity::class.java))
                true
            }
            R.id.action_debug -> {
                startActivity(Intent(this, DebugActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
