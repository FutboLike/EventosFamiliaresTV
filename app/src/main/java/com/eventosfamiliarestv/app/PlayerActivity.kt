package com.eventosfamiliarestv.app

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eventosfamiliarestv.app.databinding.ActivityPlayerBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val database by lazy { FirebaseDatabase.getInstance() }
    private var channelId = ""
    private var deviceId = ""
    private var serverTimeOffset = 0L
    private val heartbeat = object : Runnable {
        override fun run() {
            updateHeartbeat()
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveFullscreen()
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        binding.playerStatus.text = getString(R.string.playing, name)

        database.getReference(".info/serverTimeOffset").get().addOnSuccessListener {
            serverTimeOffset = it.getValue(Long::class.java) ?: 0L
        }
        heartbeatHandler.post(heartbeat)

        libVLC = LibVLC(this, arrayListOf("--network-caching=1800", "--clock-jitter=0", "--clock-synchro=0"))
        mediaPlayer = MediaPlayer(libVLC).apply {
            attachViews(binding.videoLayout, null, false, false)
            setEventListener { event ->
                runOnUiThread {
                    when (event.type) {
                        MediaPlayer.Event.Playing -> binding.playerStatus.visibility = View.GONE
                        MediaPlayer.Event.EncounteredError -> {
                            binding.playerStatus.visibility = View.VISIBLE
                            binding.playerStatus.text = getString(R.string.player_error)
                        }
                    }
                }
            }
        }

        val media = Media(libVLC, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=1800")
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    private fun enableImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Respaldo para Android y TV Box antiguos.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    override fun onStop() {
        super.onStop()
        heartbeatHandler.removeCallbacks(heartbeat)
        if (::mediaPlayer.isInitialized) mediaPlayer.stop()
        releaseSession()
    }

    override fun onDestroy() {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.detachViews()
            mediaPlayer.release()
        }
        if (::libVLC.isInitialized) libVLC.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "channel_url"
        const val EXTRA_NAME = "channel_name"
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_DEVICE_ID = "device_id"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    private fun updateHeartbeat() {
        if (channelId.isBlank() || deviceId.isBlank()) return
        val sessionRef = database.reference.child("sesiones").child(channelId)
        sessionRef.child("dispositivo").get().addOnSuccessListener { snapshot ->
            if (snapshot.getValue(String::class.java) == deviceId) {
                sessionRef.child("ultimaActividad").setValue(System.currentTimeMillis() + serverTimeOffset)
            } else {
                finish()
            }
        }
    }

    private fun releaseSession() {
        if (channelId.isBlank() || deviceId.isBlank()) return
        database.reference.child("sesiones").child(channelId)
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    return if (currentData.child("dispositivo").getValue(String::class.java) == deviceId) {
                        currentData.value = null
                        Transaction.success(currentData)
                    } else {
                        Transaction.abort()
                    }
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) = Unit
            })
    }
}
