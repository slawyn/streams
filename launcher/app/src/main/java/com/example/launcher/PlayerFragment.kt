
import androidx.fragment.app.DialogFragment
import android.view.LayoutInflater
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.BandwidthMeter

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.launcher.R

class PlayerFragment : DialogFragment() {

    private var player: ExoPlayer? = null
    private var videoUrl: String? = null
    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    private lateinit var bitrateTextView: TextView
    private lateinit var bandwidthMeter: BandwidthMeter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoUrl = arguments?.getString("VIDEO_URL")
        handler = Handler(Looper.getMainLooper())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.player, null)
        view.keepScreenOn = true

        val playerView = view.findViewById<PlayerView>(R.id.playerView)
        bitrateTextView = view.findViewById(R.id.bitrateTextView)

        bandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build()

        player = ExoPlayer.Builder(this.requireContext())
            .setBandwidthMeter(bandwidthMeter)
            .build().apply {
                playerView.player = this
                val mediaItem = MediaItem.fromUri(videoUrl!!)
                setMediaItem(mediaItem)
                prepare()
                play()
            }

        startBitrateUpdates()

        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(view)
        return dialog
    }

    private fun startBitrateUpdates() {
        updateRunnable = object : Runnable {
            override fun run() {
                val bitrate = bandwidthMeter.bitrateEstimate
                val mbps = bitrate / 1_000_000.0
                bitrateTextView.text = String.format("Bitrate: %.2f Mbps", mbps)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateRunnable)
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        handler.removeCallbacks(updateRunnable)
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
