package com.example.launcher

import LanguageLoader
import StreamLoader
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.launch

private const val BASE_URL = "http://192.168.0.108:80/"

@SuppressLint("SetJavaScriptEnabled")
class MainActivity : AppCompatActivity() {
    private lateinit var language: Language
    private lateinit var webView: WebView
    private var currentStreams: List<StreamEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        language = LanguageLoader.load(this)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // After page loaded, inject translations and request local streams to populate UI
                // The page will call Android.requestLocalStreams() but also call it here to be safe
                val translations = Gson().toJson(language)
                val js = """
                    (function() {
                        window.translations = $translations;
                        if (window.applyTranslations) window.applyTranslations();
                        if(window.requestLocalStreams) { window.requestLocalStreams(); }
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            }
        }

        webView.addJavascriptInterface(AndroidBridge(), "Android")
        webView.loadUrl("file:///android_asset/main_activity_ui.html")
    }

    private inner class AndroidBridge {

        @JavascriptInterface
        fun requestLocalStreams() {
            runOnUiThread {
                try {
                    val local = StreamLoader.loadLocal(this@MainActivity)
                    currentStreams = local
                    val json = Gson().toJson(local)
                    webView.post { webView.evaluateJavascript("window.updateStreams($json)", null) }
                } catch (e: Exception) {
                    Log.e("LOCAL_LOAD", e.message ?: "")
                }
            }
        }

        @JavascriptInterface
        fun loadRemoteStreams() {
            lifecycleScope.launch {
                try {
                    val remote = StreamLoader.loadRemote(BASE_URL)
                    currentStreams = remote
                    val json = Gson().toJson(remote)
                    webView.post { webView.evaluateJavascript("window.updateStreams($json)", null) }
                } catch (e: Exception) {
                    Log.e("API_ERROR", e.message ?: language.statusUnknownError)
                    runOnUiThread { Toast.makeText(this@MainActivity, language.statusUnknownError, Toast.LENGTH_SHORT).show() }
                }
            }
        }

        @JavascriptInterface
        fun onItemClicked(index: Int) {
            runOnUiThread { handleItemClick(index) }
        }

        @JavascriptInterface
        fun getBaseUrl(): String {
            return BASE_URL
        }
    }

    private fun handleItemClick(index: Int) {
        if (index < 0 || index >= currentStreams.size) return
        val stream = currentStreams[index]
        val streams = stream.streams
        if (streams.isEmpty()) {
            Toast.makeText(this, language.statusNoStreams, Toast.LENGTH_SHORT).show()
            return
        } else if (streams.size == 1) {
            val playerFragment = PlayerFragment.newInstance(streams[0].link)
            playerFragment.show(supportFragmentManager, "PlayerFragment")
            return
        }

        val items = streams.mapIndexed { _, s ->
            val avail = if (s.available) "" else " (unavailable)"
            "${s.id}$avail"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(language.selectStream)
            .setItems(items) { _, which ->
                val selected = streams[which]
                val playerFragment = PlayerFragment.newInstance(selected.link)
                playerFragment.show(supportFragmentManager, "PlayerFragment")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
