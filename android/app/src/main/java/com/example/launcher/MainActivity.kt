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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val versionStr = getString(R.string.version)
        val buildStr = getString(R.string.build)
        webView.addJavascriptInterface(AndroidBridge(versionStr, buildStr), "Android")
        webView.loadUrl("file:///android_asset/main-ui.html")
    }

    private inner class AndroidBridge (private val version: String, private val build: String) {

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
        fun onItemLongClicked(index: Int) {
            runOnUiThread { handleItemClick(index, true) }
        }
        @JavascriptInterface
        fun onItemClicked(index: Int) {
            runOnUiThread { handleItemClick(index, false) }
        }
        @JavascriptInterface
        fun getBaseUrl(): String {
            return BASE_URL
        }
        @JavascriptInterface
        fun getVersion(): String {
           return "$version:$build"
        }
        @JavascriptInterface
        fun loadProgram(index: Int, requestId: Long) {
            // Capture the URL on the UI side before doing any background work.
            runOnUiThread {
                if (index !in currentStreams.indices) {
                    sendProgramResult(requestId, index, null)
                    return@runOnUiThread
                }

                val url = currentStreams[index].program

                if (url.isNullOrBlank()) {
                    sendProgramResult(requestId, index, "[]")
                    return@runOnUiThread
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val result = fetchProgram(url)

                    withContext(Dispatchers.Main) {
                        sendProgramResult(requestId, index, result)
                    }
                }
            }
        }
    }

    private fun sendProgramResult(
        requestId: Long,
        index: Int, 
        result: String?
    ) {
        // Gson handles all escaping required for passing the JSON string
        // safely into JavaScript.
        val resultJson = Gson().toJson(result)

        val js = """
            window.onProgramLoaded(
                $requestId,
                $index,
                $resultJson
            );
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun fetchProgram(url: String): String? {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get()

            val channelData =
                doc.selectFirst("div.square#progListSquare #channel_data")
                    ?: return "[]"

            val programs = JSONArray()

            for (prog in channelData.select("div.prog")) {
                val time =
                    prog.selectFirst("div.time")
                        ?.text()
                        ?.trim()
                        ?: ""

                val title =
                    prog.selectFirst("div.title")
                        ?.text()
                        ?.trim()
                        ?: ""

                if (time.isBlank()) continue

                val attributes = JSONArray()

                for (attribute in prog.classNames()) {
                    if (attribute != "prog") {
                        attributes.put(attribute)
                    }
                }

                programs.put(
                    JSONObject().apply {
                        put("time", time)
                        put("title", title)
                        put("attributes", attributes)
                    }
                )
            }

            programs.toString()
        } catch (e: Exception) {
            Log.e("PROGRAM_LOAD", "Failed to load program: $url", e)
            null
        }
    }
    private fun handleItemClick(index: Int, dialog: Boolean) {
        if (index < 0 || index >= currentStreams.size) return
        val stream = currentStreams[index]
        val streams = stream.streams
        if (streams.isEmpty()) {
            Toast.makeText(this, language.statusNoStreams, Toast.LENGTH_SHORT).show()
            return
        } else if (streams.size == 1 || !dialog) {
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
