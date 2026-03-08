# BlueChat — Bluetooth Offline Chat Application

**Package:** `com.rehaan.bluetoothchat`  
**Language:** Kotlin  
**Architecture:** MVVM + Clean Architecture + Repository Pattern  
**Min SDK:** 26 (Android 8.0) | **Target SDK:** 34

---

## 📁 Project Structure

```
app/src/main/java/com/rehaan/bluetoothchat/
├── BlueChatApplication.kt          → Hilt entry point
│
├── bluetooth/
│   ├── AppBluetoothManager.kt      → Bluetooth state, discovery, device list
│   ├── BluetoothServer.kt          → Server thread (accepts connections)
│   ├── BluetoothClient.kt          → Client thread (initiates connections)
│   ├── BluetoothConnectionThread.kt→ Active stream read/write for connected socket
│   ├── BluetoothService.kt         → Foreground service orchestrator + sealed BluetoothMessage
│   └── VoiceRecorder.kt            → MediaRecorder + MediaPlayer wrapper
│
├── data/
│   ├── local/
│   │   ├── ChatDatabase.kt         → Room database
│   │   ├── dao/MessageDao.kt       → Messages + Chat session DAOs
│   │   └── entities/MessageEntity.kt → DB entity + domain mappers
│   └── repository/ChatRepository.kt→ Single source of truth
│
├── domain/model/
│   └── Message.kt                  → Message, DeviceInfo, ChatSession, etc.
│
├── ui/
│   ├── main/
│   │   ├── MainActivity.kt         → Chat session list + FAB
│   │   └── MainViewModel.kt
│   ├── devices/
│   │   ├── DeviceListActivity.kt   → Paired + nearby device scanner
│   │   └── DeviceListViewModel.kt
│   ├── chat/
│   │   ├── ChatActivity.kt         → Full chat UI, voice recording, file picker
│   │   └── ChatViewModel.kt        → Binds to BluetoothService
│   └── debug/
│       └── DebugActivity.kt        → Hidden admin panel with logs
│
├── adapters/
│   ├── MessageAdapter.kt           → Sent / Received chat bubbles
│   ├── DeviceAdapter.kt            → Device list
│   └── ChatSessionAdapter.kt       → Main screen session list
│
├── di/
│   └── DatabaseModule.kt           → Hilt DB providers
│
└── utils/
    ├── Constants.kt                → UUIDs, state codes, protocol prefixes
    ├── Extensions.kt               → Kotlin extension functions
    ├── PermissionHelper.kt         → Runtime permission utilities
    └── FileUtils.kt                → URI/file operations
```

---

## 🚀 Setup Instructions

### Step 1 — Prerequisites
- Android Studio **Hedgehog (2023.1.1)** or newer
- JDK 17
- Android SDK 34 installed
- Two physical Android devices (Bluetooth does NOT work on emulators)

### Step 2 — Open Project
1. Open Android Studio
2. File → Open → Select the `BlueChat` root folder
3. Wait for Gradle sync to complete

### Step 3 — Sync Gradle
If sync fails:
- File → Invalidate Caches → Invalidate and Restart
- Ensure Google and Maven Central are available in your network

### Step 4 — Add Launcher Icons
Place launcher icon files in:
- `res/mipmap-hdpi/ic_launcher.png` (72×72)
- `res/mipmap-mdpi/ic_launcher.png` (48×48)
- `res/mipmap-xhdpi/ic_launcher.png` (96×96)
- `res/mipmap-xxhdpi/ic_launcher.png` (144×144)
- `res/mipmap-xxxhdpi/ic_launcher.png` (192×192)

Or use Android Studio's Image Asset Studio (right-click `res` → New → Image Asset).

### Step 5 — Build & Install
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or use **Run ▶** from Android Studio with a physical device connected.

---

## 📡 How to Use

### Connecting Two Devices

**Device A (Host — Server Mode):**
1. Open BlueChat
2. Tap **+** to go to Device List
3. Tap **Make Visible** (makes device discoverable for 5 minutes)
4. Tap a device from the paired list and open chat
5. In chat, tap ⋮ menu → **Accept Incoming** — this starts the server

**Device B (Client — Connect Mode):**
1. Open BlueChat
2. Tap **+** to go to Device List
3. Tap **Scan for Devices**
4. Tap Device A from the list
5. BlueChat auto-connects as client

Once connected, the status bar turns **green** and chat is live.

### Sending Messages
- **Text:** Type in the input box, tap Send
- **Files/Images:** Tap the 📎 paperclip button
- **Voice:** Press and hold the 🎤 microphone button, release to send

### Debug Panel
- Tap ⋮ → **Debug Panel** to view:
  - Bluetooth state
  - Connection state
  - Live connection logs

---

## 🔧 Architecture Notes

### Connection Flow
```
MainActivity ──→ DeviceListActivity ──→ ChatActivity
                                              │
                                    Binds BluetoothService
                                              │
                          ┌───────────────────┴──────────────────┐
                     BluetoothServer                    BluetoothClient
                     (listens for                       (connects to
                      incoming)                          remote device)
                          │                                        │
                          └──────────── BluetoothConnectionThread ─┘
                                        (DataInputStream / DataOutputStream)
```

### Message Protocol (Binary)
Each transmission starts with a 1-byte packet type:
- `0x01` = TEXT — followed by int (length) + UTF-8 bytes
- `0x02` = FILE — followed by int (meta length) + meta string `name|size|mime` + raw bytes
- `0x03` = VOICE — followed by int (meta length) + meta string `name|duration|size` + raw bytes

### Persistence
All messages are stored in a Room SQLite database (`bluechat_db`).  
Tables: `messages`, `chat_sessions`

---

## ⚠️ Known Limitations
- Bluetooth Classic RFCOMM has ~1 Mbps theoretical throughput — large files are slow
- Max file size: **25 MB** (enforced in `FileUtils.isFileSizeAllowed`)
- Voice max duration: **2 minutes**
- One active connection at a time per device
- Bluetooth emulator support: **None** — must use real hardware

---

## 🎨 Theming
- Full Material You / Material Design 3
- Automatic **Dark Mode** support via `DayNight` theme
- Color palette defined in `res/values/colors.xml` (light) and `res/values-night/colors.xml` (dark)
- Chat bubble shapes defined in `themes.xml` as `ChatBubbleSentShape` / `ChatBubbleReceivedShape`

---

## 📋 Permissions Summary

| Permission | Purpose |
|---|---|
| `BLUETOOTH_CONNECT` | Read device names, create sockets (API 31+) |
| `BLUETOOTH_SCAN` | Discover nearby devices (API 31+) |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Legacy support (API ≤ 30) |
| `ACCESS_FINE_LOCATION` | Required for BT discovery (API < 31) |
| `RECORD_AUDIO` | Voice message recording |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_AUDIO` | Attach files from storage |
| `FOREGROUND_SERVICE` | Keep Bluetooth service alive |

---

*Built with ❤️ for Rehaan — BlueChat v1.0.0*
