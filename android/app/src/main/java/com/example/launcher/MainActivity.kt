package com.example.launcher

import GridPopulator
import LanguageLoader
import StreamLoader
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private const val BASE_URL = "http://192.168.0.108:80/"
class MainActivity : AppCompatActivity() {
    private lateinit var language: Language
    private lateinit var grid: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        language = LanguageLoader.load(this)
        grid = findViewById<GridLayout>(R.id.grid)
        val address = findViewById<TextView>(R.id.address)
        address.text = BASE_URL



        // show build number in player dialog if available
        val versionView = findViewById<TextView?>(R.id.version)
        versionView.text = buildString {
            append(getString(R.string.version))
            append(".")
            append(getString(R.string.build))
        }

        val button = findViewById<Button>(R.id.button)
        button.text = language.loadStreamsBuffer
        button.setOnClickListener {
            Toast.makeText(this, language.statusLoadingStreams, Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val remoteStreams = StreamLoader.loadRemote(BASE_URL)
                    GridPopulator.populate(this@MainActivity, grid, remoteStreams)
                } catch (e: Exception) {
                    Log.e("API_ERROR", e.message ?: language.statusUnknownError)
                    }
                }
        }

        val localStreams = StreamLoader.loadLocal(this)
        lifecycleScope.launch {
            GridPopulator.populate(this@MainActivity, grid, localStreams)
        }
    }
}
