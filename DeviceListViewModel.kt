package com.rehaan.bluetoothchat.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rehaan.bluetoothchat.bluetooth.AppBluetoothManager
import com.rehaan.bluetoothchat.data.repository.ChatRepository
import com.rehaan.bluetoothchat.domain.model.DeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    val bluetoothManager: AppBluetoothManager,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val pairedDevices: StateFlow<List<DeviceInfo>> = bluetoothManager.pairedDevices
    val discoveredDevices: StateFlow<List<DeviceInfo>> = bluetoothManager.discoveredDevices
    val isDiscovering: StateFlow<Boolean> = bluetoothManager.isDiscovering
    val isBluetoothEnabled: StateFlow<Boolean> = bluetoothManager.isBluetoothEnabled

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refreshPairedDevices()
    }

    fun refreshPairedDevices() {
        bluetoothManager.refreshPairedDevices()
    }

    fun startDiscovery(): Boolean {
        return bluetoothManager.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothManager.stopDiscovery()
    }

    fun onDeviceSelected(device: DeviceInfo) {
        viewModelScope.launch {
            chatRepository.createOrUpdateSession(device.name, device.address)
        }
    }
}
