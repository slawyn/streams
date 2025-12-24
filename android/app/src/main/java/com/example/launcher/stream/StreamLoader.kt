import androidx.appcompat.app.AppCompatActivity
import com.example.launcher.RetrofitClient
import com.example.launcher.StreamEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object StreamLoader {

    fun loadLocal(activity: AppCompatActivity): List<StreamEntry> {
        return Gson().fromJson(
            activity.assets.open("config.json").bufferedReader().use { it.readText() },
            object : TypeToken<List<StreamEntry>>() {}.type
        )
    }

    suspend fun loadRemote(baseUrl: String): List<StreamEntry> {
        RetrofitClient.setBaseUrl(baseUrl)
        val response = RetrofitClient.apiService.getStreamsRaw()

        return Gson().fromJson(
            response.string(),
            object : TypeToken<List<StreamEntry>>() {}.type
        )
    }
}
