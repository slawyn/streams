import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.widget.Button
import android.widget.GridLayout
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

        button.text = stream.name
        button.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)

        // Apply focus animation
        button.stateListAnimator = createFocusAnimator(button)

        // Click → open player
        button.setOnClickListener {
            val playerFragment = PlayerFragment.newInstance(stream.link)
            playerFragment.show(
                activity.supportFragmentManager,
                "PlayerFragment"
            )
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
