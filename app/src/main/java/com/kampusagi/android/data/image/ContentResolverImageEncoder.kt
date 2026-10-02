package com.kampusagi.android.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.ImageEncoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ContentResolverImageEncoder @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImageEncoder {

    override suspend fun avatarJpeg(uri: String): AppResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val source = decode(Uri.parse(uri)) ?: return@withContext AppResult.Failure(AppError.IMAGE_UNREADABLE)
            val side = min(source.width, source.height)
            val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
            val scaled = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
            AppResult.Success(output.toByteArray())
        } catch (e: IOException) {
            Log.w(TAG, "Picked image could not be read", e)
            AppResult.Failure(AppError.IMAGE_UNREADABLE)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read the picked image", e)
            AppResult.Failure(AppError.IMAGE_UNREADABLE)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Picked image has an unsupported format", e)
            AppResult.Failure(AppError.IMAGE_UNREADABLE)
        }
    }

    override suspend fun postPhotoJpeg(uri: String): AppResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val source = decode(Uri.parse(uri)) ?: return@withContext AppResult.Failure(AppError.IMAGE_UNREADABLE)
            val longest = maxOf(source.width, source.height)
            val scaled = if (longest > POST_MAX_SIDE) {
                val factor = POST_MAX_SIDE.toFloat() / longest
                Bitmap.createScaledBitmap(source, (source.width * factor).toInt(), (source.height * factor).toInt(), true)
            } else {
                source
            }
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, POST_QUALITY, output)
            AppResult.Success(output.toByteArray())
        } catch (e: IOException) {
            Log.w(TAG, "Picked image could not be read", e)
            AppResult.Failure(AppError.IMAGE_UNREADABLE)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read the picked image", e)
            AppResult.Failure(AppError.IMAGE_UNREADABLE)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Picked image has an unsupported format", e)
            AppResult.Failure(AppError.IMAGE_UNREADABLE)
        }
    }

    /** ImageDecoder applies the photo's rotation; on Android 8 the image is decoded as stored. */
    private fun decode(uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_DECODE) {
                    val factor = MAX_DECODE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * factor).toInt(), (info.size.height * factor).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DECODE) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }

    private companion object {
        const val TAG = "ImageEncoder"
        const val SIZE = 512
        const val MAX_DECODE = 2048
        const val QUALITY = 85
        // Sharp on phone screens and well under the bucket's 3 MB limit.
        const val POST_MAX_SIDE = 1440
        const val POST_QUALITY = 82
    }
}
