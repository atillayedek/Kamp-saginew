package com.kampusagi.android.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Storage byte limits alone do not limit the decoded pixel allocation. */
object BoundedImageDecoder {
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        require(maxSide >= 2)
        var sample = 1
        while (maxOf(width, height).toLong() > maxSide.toLong() * sample) sample *= 2
        return sample
    }

    fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        })
    }
}
