package com.example.launcher

import PlayerFragment
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch


var LANGUAGE = Language()
val BASE_URL = "192.168.0.108:80"

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        /* Init language */
        LANGUAGE = Gson().fromJson(
            assets.open("lang-ru.json").bufferedReader().use { it.readText() },
            object: TypeToken<Language>() {}.type)

        /* 🔽 Initial load from config.json */
        val localStreams: List<StreamEntry> = Gson().fromJson(
            assets.open("config.json").bufferedReader().use { it.readText() },
            object : TypeToken<List<StreamEntry>>() {}.type)

        /* Load new buttons async */
        val grid = findViewById<GridLayout>(R.id.grid)

        val textView = findViewById<TextView>(R.id.textView)
        textView.text = BASE_URL

        val button = findViewById<Button>(R.id.button)
        button.text = LANGUAGE.loadStreamsBuffer;
        button.setOnClickListener {
            Toast.makeText(this, LANGUAGE.statusLoadingStreams, Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                try {
                    RetrofitClient.setBaseUrl(BASE_URL)

                    val response = RetrofitClient.apiService.getStreamsRaw()
                    val streams: List<StreamEntry> = Gson().fromJson(
                        response.string(),
                        object : TypeToken<List<StreamEntry>>() {}.type
                    )

                    // Clear old views before adding new ones
                    grid.removeAllViews()
                    populateGrid(grid, streams)

                } catch (e: Exception) {
                    Log.e("API_ERROR", e.message ?: LANGUAGE.statusUnknownError)
                }
            }
        }


        lifecycleScope.launch {
            populateGrid(grid, localStreams)
        }
    }

    private suspend fun populateGrid(grid: GridLayout, streams: List<StreamEntry>) {

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

                // Make the button focusable so it can be "selected"
                isFocusable = true
                isFocusableInTouchMode = true
                stateListAnimator = StateListAnimator().apply {

                    // Focused OR Selected → scale up
                    addState(
                        intArrayOf(
                            android.R.attr.state_focused,
                            android.R.attr.state_selected
                        ),
                        AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(this@apply, "scaleX", 1.1f),
                                ObjectAnimator.ofFloat(this@apply, "scaleY", 1.1f)
                            )
                            duration = 120
                        }
                    )

                    // Focused only → scale up
                    addState(
                        intArrayOf(android.R.attr.state_focused),
                        AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(this@apply, "scaleX", 1.1f),
                                ObjectAnimator.ofFloat(this@apply, "scaleY", 1.1f)
                            )
                            duration = 120
                        }
                    )

                    // Selected only → scale up
                    addState(
                        intArrayOf(android.R.attr.state_selected),
                        AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(this@apply, "scaleX", 1.1f),
                                ObjectAnimator.ofFloat(this@apply, "scaleY", 1.1f)
                            )
                            duration = 120
                        }
                    )

                    // Default → scale back down
                    addState(
                        intArrayOf(),
                        AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(this@apply, "scaleX", 1f),
                                ObjectAnimator.ofFloat(this@apply, "scaleY", 1f)
                            )
                            duration = 120
                        }
                    )
                }

                setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)
                setOnClickListener {
                    val playerFragment = PlayerFragment.newInstance(stream.link)
                    playerFragment.show(
                        (grid.context as AppCompatActivity).supportFragmentManager,
                        "PlayerFragment"
                    )
                }
            }

            grid.addView(streamView)
        }
    }
}