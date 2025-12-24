import android.graphics.drawable.BitmapDrawable
import android.widget.GridLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.launcher.Cache
import com.example.launcher.StreamEntry

object GridPopulator {

    suspend fun populate(
        activity: AppCompatActivity,
        grid: GridLayout,
        streams: List<StreamEntry>
    ) {
        grid.removeAllViews()

        streams.forEach { stream ->
            val bitmap = Cache.getImage(activity, stream.logo)
            val drawable = bitmap?.let { BitmapDrawable(activity.resources, it) }

            val button = StreamButtonFactory.create(activity, grid, stream, drawable)
            grid.addView(button)
        }
    }
}
