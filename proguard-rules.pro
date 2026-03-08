# Add project specific ProGuard rules here.
-keep class com.rehaan.bluetoothchat.** { *; }

# Hilt
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Timber
-dontwarn org.jetbrains.annotations.**
