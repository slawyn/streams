import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.PlayerView
import com.example.launcher.R
import java.io.IOException
import java.util.Locale

class PlayerFragment : DialogFragment() {

    private var player: ExoPlayer? = null
    private var videoUrl: String? = null
    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    private lateinit var bitrateTextView: TextView
    private lateinit var playbackModeSpinner: Spinner
    private var autoSpeedEnabled: Boolean = true
    private lateinit var bandwidthMeter: BandwidthMeter

    // Current media and managers
    private var currentMediaItem: MediaItem? = null

    // Constants and helpers
    private val TAG = "PlayerFragment"
    private val RETRY_DELAY_MS = 3000L
    private val retryManager = RetryManager()

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

        player =
                ExoPlayer.Builder(requireContext())
                        .setBandwidthMeter(bandwidthMeter)
                        .build()
                        .also { exo -> initPlayer(exo, playerView) }

        startBitrateUpdates()

        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        dialog.setContentView(view)
        return dialog
    }

    private fun startBitrateUpdates() {
        updateRunnable =
                object : Runnable {
                    override fun run() {
                        updateBitrateUi()
                        handler.postDelayed(this, 1000)
                    }
                }

        handler.post(updateRunnable)
    }

    private fun initViews(root: View): PlayerView {
        bitrateTextView = root.findViewById(R.id.bitrateTextView)
        playbackModeSpinner = root.findViewById(R.id.playbackModeSpinner)

        val adapter =
                ArrayAdapter.createFromResource(
                        root.context,
                        R.array.playback_speed_modes,
                        android.R.layout.simple_spinner_item
                )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        playbackModeSpinner.adapter = adapter
        playbackModeSpinner.setSelection(0)

        playbackModeSpinner.setOnItemSelectedListener(
                object : android.widget.AdapterView.OnItemSelectedListener {

                    override fun onItemSelected(
                            parent: android.widget.AdapterView<*>,
                            view: View?,
                            position: Int,
                            id: Long
                    ) {
                        autoSpeedEnabled = position == 0
                        updateBitrateUi()
                    }

                    override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
                }
        )

        bitrateTextView.setOnClickListener { playbackModeSpinner.performClick() }

        return root.findViewById(R.id.playerView)
    }

    private fun initBandwidthMeter() {
        bandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()
    }

    /**
     * HTTP factory used for HLS requests.
     *
     * The stream you provided appears to use a signed WMS-style authentication query parameter, so
     * we want ExoPlayer's HTTP behavior to be as permissive as possible.
     */
    private fun createHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
                .setUserAgent(
                        "Mozilla/5.0 (Android) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                )
                /* TODO! use "referer" option from "streams" to set this */
                .setDefaultRequestProperties(mapOf("Referer" to "https://televizor24.tv/"))
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
    }

    private fun createHlsMediaSource(url: String): HlsMediaSource {

        val mediaItem =
                MediaItem.Builder()
                        .setUri(url)

                        // Explicitly tell Media3 that this is HLS.
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()

        currentMediaItem = mediaItem

        val httpFactory = createHttpDataSourceFactory()

        return HlsMediaSource.Factory(httpFactory)

                // Some unusual/live playlists don't expose enough
                // codec information for chunkless preparation.
                .setAllowChunklessPreparation(false)
                .createMediaSource(mediaItem)
    }

    private fun initPlayer(exo: ExoPlayer, playerView: PlayerView) {
        playerView.player = exo

        val url = videoUrl

        if (url.isNullOrBlank()) {
            android.util.Log.e(TAG, "VIDEO_URL is empty")
            return
        }

        android.util.Log.d(TAG, "Starting HLS:")
        android.util.Log.d(TAG, url)

        /*
         * IMPORTANT:
         *
         * Install the listener BEFORE prepare().
         * Otherwise an early preparation error can happen before
         * the listener is attached.
         */
        setupPlayerListener(exo)

        val mediaSource = createHlsMediaSource(url)

        exo.setMediaSource(mediaSource)

        exo.prepare()
        exo.play()
    }

    private fun setupPlayerListener(exo: ExoPlayer) {

        exo.addListener(
                object : Player.Listener {

                    override fun onPlayerError(error: PlaybackException) {

                        logPlaybackError(error)

                        val causeMsg = error.cause?.message ?: ""

                        if (causeMsg.contains("404", ignoreCase = true) ||
                                        causeMsg.contains("403", ignoreCase = true) ||
                                        causeMsg.contains("401", ignoreCase = true) ||
                                        causeMsg.contains("Not Found", ignoreCase = true) ||
                                        causeMsg.contains("Forbidden", ignoreCase = true) ||
                                        causeMsg.contains("Unable to connect", ignoreCase = true) ||
                                        causeMsg.contains(
                                                "Cleartext HTTP traffic",
                                                ignoreCase = true
                                        ) ||
                                        error.cause is java.io.FileNotFoundException ||
                                        error.cause is IOException
                        ) {
                            android.util.Log.d(TAG, "Playback error detected, scheduling retry")

                            retryManager.schedule()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                android.util.Log.d(TAG, "Player BUFFERING")

                                updateBitrateUi()
                            }
                            Player.STATE_READY -> {
                                android.util.Log.d(TAG, "Player READY")

                                updateBitrateUi()
                                retryManager.cancel()
                            }
                            Player.STATE_ENDED -> {
                                android.util.Log.d(TAG, "Player ENDED")
                            }
                            Player.STATE_IDLE -> {
                                android.util.Log.d(TAG, "Player IDLE")
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        android.util.Log.d(TAG, "isPlaying=$isPlaying")

                        if (isPlaying) {
                            updateBitrateUi()
                            retryManager.cancel()
                        }
                    }
                }
        )
    }

    /**
     * Log the complete cause chain.
     *
     * This is especially important for this stream because we need to distinguish:
     *
     * 403/401 -> signed URL/authentication problem 404 -> wrong segment URL parser ->
     * malformed/unusual HLS decoder -> unsupported codec timeout -> CDN/network issue
     */
    private fun logPlaybackError(error: PlaybackException) {

        android.util.Log.e(TAG, "========== EXOPLAYER ERROR ==========")

        android.util.Log.e(TAG, "errorCode=${error.errorCode}")

        android.util.Log.e(TAG, "errorCodeName=${error.errorCodeName}")

        android.util.Log.e(TAG, "message=${error.message}")

        var cause: Throwable? = error.cause
        var level = 0

        while (cause != null) {

            android.util.Log.e(
                    TAG,
                    "CAUSE[$level] " + "${cause.javaClass.name}: " + "${cause.message}"
            )

            cause = cause.cause
            level++
        }

        android.util.Log.e(TAG, "======================================")
    }

    private fun updateBitrateUi() {

        val bitrate = bandwidthMeter.bitrateEstimate
        val mbps = bitrate / 1_000_000.0

        val bufferedMs =
                player?.let { kotlin.math.max(0L, it.bufferedPosition - it.currentPosition) } ?: 0L

        val bufferedSec = bufferedMs / 1000.0

        if (autoSpeedEnabled) {

            player?.let {
                val targetSpeed =
                        if (bufferedSec < 5.0) {
                            0.8f
                        } else {
                            1.0f
                        }

                val currentSpeed = it.playbackParameters.speed

                if (kotlin.math.abs(currentSpeed - targetSpeed) > 0.01f) {
                    it.setPlaybackParameters(PlaybackParameters(targetSpeed))
                }
            }
        }

        val statusChar = if (autoSpeedEnabled) "A" else "D"

        handler.post {
            bitrateTextView.text =
                    String.format(
                            Locale.US,
                            "%6.2f Mbps | %6.2f s | %s",
                            mbps,
                            bufferedSec,
                            statusChar
                    )
        }
    }

    private fun stopBitrateUpdates() {
        if (::updateRunnable.isInitialized) {
            handler.removeCallbacks(updateRunnable)
        }
    }

    private inner class RetryManager {

        private var runnable: Runnable? = null
        private var retrying = false

        fun schedule() {

            if (retrying) {
                return
            }

            retrying = true

            runnable =
                    object : Runnable {

                        override fun run() {

                            val url = videoUrl

                            if (url.isNullOrBlank()) {
                                retrying = false
                                return
                            }

                            player?.let { exo ->
                                if (exo.isPlaying) {
                                    runnable?.let { handler.removeCallbacks(it) }

                                    runnable = null
                                    retrying = false
                                    return
                                }

                                android.util.Log.d(TAG, "Retrying HLS playback")

                                try {

                                    /*
                                     * Recreate the HLS MediaSource rather than
                                     * simply reusing the old one.
                                     */
                                    val mediaSource = createHlsMediaSource(url)

                                    exo.setMediaSource(mediaSource)
                                    exo.prepare()
                                    exo.play()
                                } catch (error: Exception) {

                                    android.util.Log.e(TAG, "Retry failed", error)
                                }
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

    override fun onStop() {

        super.onStop()

        retryManager.cancel()

        player?.release()
        player = null

        stopBitrateUpdates()
    }

    companion object {

        fun newInstance(url: String): PlayerFragment {

            val fragment = PlayerFragment()

            fragment.arguments = Bundle().apply { putString("VIDEO_URL", url) }

            return fragment
        }
    }
}
