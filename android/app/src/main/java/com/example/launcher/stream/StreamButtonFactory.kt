
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.widget.Button
import android.widget.GridLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.launcher.R
import com.example.launcher.StreamEntry

object StreamButtonFactory {

    fun create(
        activity: AppCompatActivity,
        grid: GridLayout,
        stream: StreamEntry,
        drawable: BitmapDrawable?
    ): Button {

        // Inflate the template instead of creating a raw Button()
        val button = LayoutInflater.from(activity)
            .inflate(R.layout.button, grid, false) as Button

        button.layoutParams = GridLayout.LayoutParams().apply {
            width = GridLayout.LayoutParams.WRAP_CONTENT
            height = GridLayout.LayoutParams.WRAP_CONTENT

            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)

            setMargins(8, 8, 8, 8)
        }

        button.text = stream.name.let { if (it.length > 30) it.take(30) + "…" else it }
        button.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)

        // Apply focus animation
        button.stateListAnimator = createFocusAnimator(button)

        // Click → choose one stream (if multiple) then open player
        button.setOnClickListener {
            val language = LanguageLoader.load(activity)
            val streams = stream.streams
            if (streams.isEmpty()) {
                Toast.makeText(activity, language.statusNoStreams, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (streams.size == 1) {
                val playerFragment = PlayerFragment.newInstance(streams[0].link)
                playerFragment.show(activity.supportFragmentManager, "PlayerFragment")
                return@setOnClickListener
            }

            val items = streams.mapIndexed { index, s ->
                val avail = if (s.available) "" else " (unavailable)"
                "${s.id} $index $avail "
            }.toTypedArray()


            AlertDialog.Builder(activity)
                .setTitle(language.selectStream)
                .setItems(items) { _, which ->
                    val selected = streams[which]
                    val playerFragment = PlayerFragment.newInstance(selected.link)
                    playerFragment.show(activity.supportFragmentManager, "PlayerFragment")
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        return button
    }

    private fun createFocusAnimator(target: Button): StateListAnimator {
        return StateListAnimator().apply {

            fun scale(x: Float, y: Float) = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(target, "scaleX", x),
                    ObjectAnimator.ofFloat(target, "scaleY", y)
                )
                duration = 120
            }

            addState(intArrayOf(android.R.attr.state_focused), scale(1.2f, 1.2f))
            addState(intArrayOf(android.R.attr.state_selected), scale(1.2f, 1.2f))
            addState(intArrayOf(), scale(1f, 1f))
        }
    }
}
