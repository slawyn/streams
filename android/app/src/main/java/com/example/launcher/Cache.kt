package com.example.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object Cache {
    private val memoryCache = mutableMapOf<String, Bitmap>()

    suspend fun getImage(context: Context, url: String): Bitmap? {
        // Return from cache if exists
        memoryCache[url]?.let { return it }

        // Download if not cached
        val bitmap = downloadImage(url) ?: return null
        val scaledBitmap  = Bitmap.createScaledBitmap(bitmap, 100, 100, false);

        // Cache and return
        memoryCache[url] = scaledBitmap
        return scaledBitmap
    }

    private suspend fun downloadImage(url: String): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            val input = URL(url).openStream()
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}