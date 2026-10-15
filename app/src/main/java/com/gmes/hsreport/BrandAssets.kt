package com.gmes.hsreport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/** Loads the supplied GMES brand artwork from compact base64 assets. */
object BrandAssets {
    private var gmesBitmap: Bitmap? = null
    private var thinkSmartBitmap: Bitmap? = null

    fun gmes(context: Context): Bitmap? {
        gmesBitmap?.let { return it }
        return load(context, "gmes_logo.b64").also { gmesBitmap = it }
    }

    fun thinkSmart(context: Context): Bitmap? {
        thinkSmartBitmap?.let { return it }
        return load(context, "think_smart.b64").also { thinkSmartBitmap = it }
    }

    private fun load(context: Context, assetName: String): Bitmap? = try {
        val encoded = context.assets.open(assetName).bufferedReader().use { it.readText() }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}
