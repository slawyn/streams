package com.example.launcher

import PlayerFragment
import android.app.FragmentManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.example.launcher.ui.theme.LauncherTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET


interface ApiService {
    @GET("api/streams")
    suspend fun getStreamsRaw(): ResponseBody
}
data class StreamResponse(
    val id: String,
    val logo: String,
    val group: String,
    val name: String,
    val link: String,
    val type: String,
    val available: Boolean
)

object RetrofitClient {
    private const val BASE_URL = "http://192.168.0.108:80"
    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        val myButton = findViewById<Button>(R.id.myButton)
        val myGrid = findViewById<GridLayout>(R.id.myGrid)
        myButton.setOnClickListener {
            Toast.makeText(this, "Button clicked!", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                try {
                    val rawResponse = RetrofitClient.apiService.getStreamsRaw()
                    val json = rawResponse.string()
                    val type = object : TypeToken<List<StreamResponse>>() {}.type
                    val streams: List<StreamResponse> = Gson().fromJson(json, type)

                    streams.forEach { stream ->
                        val streamView = Button(myGrid.context).apply {
                            text = stream.name
                            layoutParams = GridLayout.LayoutParams().apply {
                                width = GridLayout.LayoutParams.WRAP_CONTENT
                                height = GridLayout.LayoutParams.WRAP_CONTENT

                                // Optional: span across more columns or rows
                                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                                setMargins(8, 8, 8, 8)
                            }
                            setOnClickListener {
                                val playerFragment = PlayerFragment.newInstance(stream.link)
                                playerFragment.show(supportFragmentManager, "PlayerFragment")
                            }
                        }
                        myGrid.addView(streamView)
                    }

                } catch (e: Exception) {
                    Log.e("API_ERROR", e.message ?: "Unknown error")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
            text = "Hello $name!",
            modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LauncherTheme {
        Greeting("Android")
    }
}