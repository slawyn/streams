import androidx.appcompat.app.AppCompatActivity
import com.example.launcher.Language
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object LanguageLoader {
    fun load(activity: AppCompatActivity): Language {
        return Gson().fromJson(
            activity.assets.open("lang-ru.json").bufferedReader().use { it.readText() },
            object : TypeToken<Language>() {}.type
        )
    }
}
