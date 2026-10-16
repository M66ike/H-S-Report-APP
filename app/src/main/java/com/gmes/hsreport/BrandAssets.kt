package com.gmes.hsreport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Loads the supplied GMES and Think SMART artwork embedded with the app. */
object BrandAssets {
    private var gmesBitmap: Bitmap? = null
    private var thinkSmartBitmap: Bitmap? = null

    fun gmes(context: Context): Bitmap? {
        gmesBitmap?.let { return it }
        return load(context, "gmes_logo.jpg").also { gmesBitmap = it }
    }

    fun thinkSmart(context: Context): Bitmap? {
        thinkSmartBitmap?.let { return it }
        return load(context, "think_smart.jpg").also { thinkSmartBitmap = it }
    }

    private fun load(context: Context, assetName: String): Bitmap? = try {
        context.assets.open(assetName).use(BitmapFactory::decodeStream)
    } catch (_: Exception) {
        null
    }
}
