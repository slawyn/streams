
import android.app.Dialog
import androidx.fragment.app.DialogFragment
import android.os.Bundle
import android.view.LayoutInflater
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.launcher.R

class PlayerFragment : DialogFragment() {

    private var player: ExoPlayer? = null
    private var videoUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoUrl = arguments?.getString("VIDEO_URL")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.player, null)
        val playerView = view.findViewById<PlayerView>(R.id.playerView)

        player = ExoPlayer.Builder(this.requireContext()).build().apply {
            playerView.player = this
            val mediaItem = MediaItem.fromUri(videoUrl!!)
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(view)
        return dialog
    }

    override fun onStop() {
        super.onStop()
        player?.release()
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
