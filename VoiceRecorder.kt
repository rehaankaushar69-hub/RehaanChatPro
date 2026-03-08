package com.rehaan.bluetoothchat.bluetooth

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.rehaan.bluetoothchat.utils.FileUtils
import timber.log.Timber
import java.io.File
import java.io.IOException

class VoiceRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var outputFile: File? = null
    private var recordingStartTime = 0L

    var isRecording = false
        private set
    var isPlaying = false
        private set

    fun startRecording(): Boolean {
        return try {
            val dir = FileUtils.getVoiceRecordingDir(context)
            outputFile = File(dir, FileUtils.generateVoiceFileName())

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile!!.absolutePath)
                setMaxDuration(120_000) // 2 minutes
                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            Timber.d("VoiceRecorder: Recording started → ${outputFile?.absolutePath}")
            true
        } catch (e: IOException) {
            Timber.e(e, "VoiceRecorder: Failed to start recording")
            releaseRecorder()
            false
        } catch (e: IllegalStateException) {
            Timber.e(e, "VoiceRecorder: Invalid state for recording")
            releaseRecorder()
            false
        }
    }

    fun stopRecording(): VoiceRecordingResult? {
        if (!isRecording) return null
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val duration = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
            val file = outputFile ?: return null

            if (!file.exists() || file.length() == 0L) {
                Timber.w("VoiceRecorder: Output file is empty or missing")
                return null
            }

            Timber.d("VoiceRecorder: Recording stopped. Duration=${duration}s, Size=${file.length()} bytes")
            VoiceRecordingResult(file, duration)
        } catch (e: Exception) {
            Timber.e(e, "VoiceRecorder: Failed to stop recording")
            releaseRecorder()
            null
        }
    }

    fun cancelRecording() {
        if (!isRecording) return
        mediaRecorder?.apply {
            try { stop() } catch (e: Exception) { Timber.w(e, "Error stopping recorder") }
            release()
        }
        mediaRecorder = null
        isRecording = false
        outputFile?.delete()
        outputFile = null
        Timber.d("VoiceRecorder: Recording cancelled")
    }

    fun playVoice(
        filePath: String,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    isPlaying = false
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    isPlaying = false
                    onError("Playback error: what=$what extra=$extra")
                    true
                }
            }
            isPlaying = true
            Timber.d("VoiceRecorder: Playing $filePath")
        } catch (e: Exception) {
            Timber.e(e, "VoiceRecorder: Failed to play audio")
            onError("Failed to play: ${e.message}")
        }
    }

    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isPlaying = false
    }

    fun getPlaybackProgress(): Int {
        return try {
            val player = mediaPlayer ?: return 0
            if (player.duration == 0) return 0
            (player.currentPosition.toFloat() / player.duration * 100).toInt()
        } catch (e: Exception) {
            0
        }
    }

    fun getPlaybackDuration(): Int {
        return try {
            mediaPlayer?.duration?.div(1000) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun releaseRecorder() {
        try { mediaRecorder?.release() } catch (e: Exception) { }
        mediaRecorder = null
        isRecording = false
    }

    fun release() {
        cancelRecording()
        stopPlayback()
    }

    data class VoiceRecordingResult(val file: File, val durationSeconds: Int)
}
