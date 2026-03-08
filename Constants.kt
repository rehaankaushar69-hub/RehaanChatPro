package com.rehaan.bluetoothchat.utils

import java.util.UUID

object Constants {

    // Bluetooth UUIDs
    val APP_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    val SECURE_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    val INSECURE_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a67")

    // Service Name
    const val SERVICE_NAME = "BlueChat"

    // Message Types
    const val MESSAGE_TYPE_TEXT = 1
    const val MESSAGE_TYPE_IMAGE = 2
    const val MESSAGE_TYPE_DOCUMENT = 3
    const val MESSAGE_TYPE_VOICE = 4
    const val MESSAGE_TYPE_FILE = 5

    // Transfer Protocols
    const val PROTOCOL_TEXT = "TEXT:"
    const val PROTOCOL_FILE_START = "FILE_START:"
    const val PROTOCOL_FILE_DATA = "FILE_DATA:"
    const val PROTOCOL_FILE_END = "FILE_END:"
    const val PROTOCOL_VOICE_START = "VOICE_START:"
    const val PROTOCOL_VOICE_DATA = "VOICE_DATA:"
    const val PROTOCOL_VOICE_END = "VOICE_END:"

    // Buffer Size
    const val BUFFER_SIZE = 65536  // 64 KB

    // Connection States
    const val STATE_NONE = 0
    const val STATE_LISTEN = 1
    const val STATE_CONNECTING = 2
    const val STATE_CONNECTED = 3

    // Intent Extras
    const val EXTRA_DEVICE_ADDRESS = "device_address"
    const val EXTRA_DEVICE_NAME = "device_name"
    const val EXTRA_CHAT_ID = "chat_id"

    // Handler Message Codes
    const val MSG_STATE_CHANGE = 1
    const val MSG_READ = 2
    const val MSG_WRITE = 3
    const val MSG_DEVICE_NAME = 4
    const val MSG_TOAST = 5
    const val MSG_FILE_PROGRESS = 6
    const val MSG_FILE_COMPLETE = 7

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "bluechat_service"
    const val NOTIFICATION_CHANNEL_NAME = "BlueChat Service"
    const val NOTIFICATION_ID = 1001

    // Database
    const val DATABASE_NAME = "bluechat_db"
    const val DATABASE_VERSION = 1

    // File
    const val MAX_FILE_SIZE_MB = 25
    const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024

    // Voice
    const val MAX_VOICE_DURATION_SECONDS = 120
    const val VOICE_SAMPLE_RATE = 44100

    // Shared Preferences
    const val PREFS_NAME = "bluechat_prefs"
    const val PREF_DEVICE_NAME = "device_name"
    const val PREF_THEME = "theme_mode"

    // Discovery
    const val DISCOVERY_DURATION_SECONDS = 300
}
