
import androidx.fragment.app.DialogFragment
import android.view.LayoutInflater
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.launcher.R
import java.util.Locale

class PlayerFragment : DialogFragment() {

    private var player: ExoPlayer? = null
    private var videoUrl: String? = null
    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    private lateinit var bitrateTextView: TextView
    private lateinit var bandwidthMeter: BandwidthMeter

    // Current media and managers
    private var currentMediaItem: MediaItem? = null

    // Constants and helpers
    private val TAG = "PlayerFragment"
    private val RETRY_DELAY_MS = 3000L
    private val retryManager = RetryManager()
    private val responseTracker = ResponseTimeTracker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoUrl = arguments?.getString("VIDEO_URL")
        handler = Handler(Looper.getMainLooper())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.player, null)
        view.keepScreenOn = true

        val playerView = initViews(view)
        initBandwidthMeter()

        player = ExoPlayer.Builder(this.requireContext())
            .setBandwidthMeter(bandwidthMeter)
            .build().also { exo ->
                initPlayer(exo, playerView)
            }

        startBitrateUpdates()

        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(view)
        return dialog
    }

    private fun startBitrateUpdates() {
        updateRunnable = object : Runnable {
            override fun run() {
                updateBitrateUi()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateRunnable)
    }

    // Helpers and small managers to improve structure

    private fun initViews(root: android.view.View): PlayerView {
        bitrateTextView = root.findViewById(R.id.bitrateTextView)
        return root.findViewById(R.id.playerView)
    }

    private fun initBandwidthMeter() {
        bandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()
    }

    private fun initPlayer(exo: ExoPlayer, playerView: PlayerView) {
        playerView.player = exo
        val url = videoUrl ?: return
        val mediaItem = MediaItem.fromUri(url)
        currentMediaItem = mediaItem
        responseTracker.startRequest()
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.play()
        setupPlayerListener(exo)
    }

    private fun setupPlayerListener(exo: ExoPlayer) {
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val causeMsg = error.cause?.message ?: ""
                if (causeMsg.contains("404") || causeMsg.contains("Not Found", ignoreCase = true) || causeMsg.contains("Unable to connect", ignoreCase = true) || error.cause is java.io.FileNotFoundException || error.cause is java.io.IOException) {
                    android.util.Log.d(TAG, "Playback error detected, scheduling retry: ${error.message}")
                    retryManager.schedule()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    responseTracker.recordResponse()
                    updateBitrateUi()
                    retryManager.cancel()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    responseTracker.recordResponse()
                    updateBitrateUi()
                    retryManager.cancel()
                }
            }
        })
    }

    private fun updateBitrateUi() {
        val bitrate = bandwidthMeter.bitrateEstimate
        val mbps = bitrate / 1_000_000.0
        val resp = responseTracker.getResponseLabel()
        handler.post {
            bitrateTextView.text = String.format("Bitrate: %.2f Mbps%s", mbps, resp)
        }
    }

    private fun stopBitrateUpdates() {
        updateRunnable?.let { handler.removeCallbacks(it) }
    }

    private inner class RetryManager {
        private var runnable: Runnable? = null
        private var retrying = false

        fun schedule() {
            if (retrying) return
            retrying = true
            runnable = object : Runnable {
                override fun run() {
                    val url = videoUrl ?: return
                    val media = currentMediaItem ?: MediaItem.fromUri(url).also { currentMediaItem = it }
                    player?.let {
                        responseTracker.startRequest()
                        it.setMediaItem(media)
                        it.prepare()
                        it.play()
                    }
                    handler.postDelayed(this, RETRY_DELAY_MS)
                }
            }
            handler.postDelayed(runnable!!, RETRY_DELAY_MS)
        }

        fun cancel() {
            runnable?.let { handler.removeCallbacks(it) }
            runnable = null
            retrying = false
        }
    }

    private inner class ResponseTimeTracker {
        private var startMs: Long = 0L
        private var lastMs: Long? = null

        fun startRequest() {
            startMs = System.currentTimeMillis()
        }

        fun recordResponse() {
            if (startMs > 0L) {
                lastMs = System.currentTimeMillis() - startMs
                startMs = 0L
            }
        }

        fun getResponseLabel(): String = lastMs?.let { String.format(Locale.US, " | Response: %.2f s", it / 1000.0) } ?: ""
    }

    override fun onStop() {
        super.onStop()
        retryManager.cancel()
        player?.release()
        stopBitrateUpdates()
    }

    companion object {
        fun newInstance(url: String): PlayerFragment {
            val fragment = PlayerFragment()
            fragment.arguments = Bundle().apply {
                putString("VIDEO_URL", url)
            }
            return fragment
        }
    }
}
