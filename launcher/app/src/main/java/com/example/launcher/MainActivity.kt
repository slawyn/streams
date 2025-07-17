package com.example.launcher

import PlayerFragment
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
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
    fun getBaseUrl(): String {
        return BASE_URL
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        val myButton = findViewById<Button>(R.id.myButton)
        val myGrid = findViewById<GridLayout>(R.id.myGrid)
        val myTextView = findViewById<TextView>(R.id.myTextView)
        myTextView.text = RetrofitClient.getBaseUrl()

        // 🔽 Initial load from config.json
        val localJsonString = assets.open("config.json").bufferedReader().use { it.readText() }
        val localType = object : TypeToken<List<StreamResponse>>() {}.type
        val localStreams: List<StreamResponse> = Gson().fromJson(localJsonString, localType)

        /* Load default buttons async */
        lifecycleScope.launch {
            populateGrid(myGrid, localStreams)
        }

        /* Load new buttons async */
        myButton.setOnClickListener {
            Toast.makeText(this, "Loading streams..", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                try {
                    val rawResponse = RetrofitClient.apiService.getStreamsRaw()
                    val remoteJson = rawResponse.string()
                    val remoteType = object : TypeToken<List<StreamResponse>>() {}.type
                    val remoteStreams: List<StreamResponse> = Gson().fromJson(remoteJson, remoteType)

                    // Clear old views before adding new ones
                    myGrid.removeAllViews()
                    populateGrid(myGrid, remoteStreams)

                } catch (e: Exception) {
                    Log.e("API_ERROR", e.message ?: "Unknown error")
                }
            }
        }
    }

    private suspend fun populateGrid(grid: GridLayout, streams: List<StreamResponse>) {
        streams.forEach { stream ->
            val bitmap  = Cache.getImage(context = this@MainActivity, url = stream.logo)
            val drawable = bitmap?.let { BitmapDrawable(resources, it) }
            val streamView = Button(grid.context).apply {
                text = stream.name
                layoutParams = GridLayout.LayoutParams().apply {
                    width = GridLayout.LayoutParams.WRAP_CONTENT
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                //setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null) // Image on top
                setOnClickListener {
                    val playerFragment = PlayerFragment.newInstance(stream.link)
                    playerFragment.show((grid.context as AppCompatActivity).supportFragmentManager, "PlayerFragment")
                }
            }
            grid.addView(streamView)
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