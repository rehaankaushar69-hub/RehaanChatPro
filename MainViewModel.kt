package com.rehaan.bluetoothchat.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rehaan.bluetoothchat.bluetooth.AppBluetoothManager
import com.rehaan.bluetoothchat.data.repository.ChatRepository
import com.rehaan.bluetoothchat.domain.model.ChatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val bluetoothManager: AppBluetoothManager,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val isBluetoothEnabled: StateFlow<Boolean> = bluetoothManager.isBluetoothEnabled

    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()

    init {
        loadChatSessions()
        bluetoothManager.refreshPairedDevices()
    }

    private fun loadChatSessions() {
        viewModelScope.launch {
            chatRepository.getAllChatSessions().collect { sessions ->
                _chatSessions.value = sessions
            }
        }
    }

    fun deleteSession(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(chatId)
        }
    }
}
