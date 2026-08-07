package com.eventosfamiliarestv.app

import android.net.Uri
import android.os.Bundle
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

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveFullscreen()
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        binding.playerStatus.text = getString(R.string.playing, name)

        libVLC = LibVLC(this, arrayListOf("--network-caching=1800", "--clock-jitter=0", "--clock-synchro=0"))
        mediaPlayer = MediaPlayer(libVLC).apply {
            vlcVout.setVideoView(binding.videoSurface)
            vlcVout.attachViews()
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
        if (::mediaPlayer.isInitialized) mediaPlayer.stop()
    }

    override fun onDestroy() {
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.vlcVout.detachViews()
            mediaPlayer.release()
        }
        if (::libVLC.isInitialized) libVLC.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "channel_url"
        const val EXTRA_NAME = "channel_name"
    }
}
