package com.kampusagi.android.presentation.common.media

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kampusagi.android.core.di.ApplicationScope
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.CommunityRepository
import com.kampusagi.android.domain.repository.GroupRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which private bucket a photo lives in. */
enum class PhotoSource { POST, GROUP }

sealed interface PhotoState {
    data object Loading : PhotoState
    data class Loaded(val image: ImageBitmap) : PhotoState
    data object Failed : PhotoState
}

/**
 * Downloads post and group photos with the signed-in session (the buckets are
 * private) and keeps the most recently shown ones in memory, up to [MAX_BYTES].
 */
@Singleton
class PostPhotoLoader @Inject constructor(
    private val posts: CommunityRepository,
    private val groups: GroupRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val states = mutableStateMapOf<String, PhotoState>()
    private val loadedOrder = ArrayDeque<String>()
    private var loadedBytes = 0L

    /** Loading until [request] finishes. Must be called on the main thread. */
    fun state(source: PhotoSource, path: String): PhotoState = states[cacheKey(source, path)] ?: PhotoState.Loading

    fun request(source: PhotoSource, path: String) {
        val key = cacheKey(source, path)
        if (states[key] != null) return
        states[key] = PhotoState.Loading
        scope.launch(Dispatchers.Main) { load(source, path, key) }
    }

    fun retry(source: PhotoSource, path: String) {
        val key = cacheKey(source, path)
        if (states[key] == PhotoState.Failed) {
            states.remove(key)
            request(source, path)
        }
    }

    private fun cacheKey(source: PhotoSource, path: String) = "${source.name}:$path"

    /** On sign-out, so the next person starts clean. */
    fun clear() {
        states.clear()
        loadedOrder.clear()
        loadedBytes = 0
    }

    private suspend fun load(source: PhotoSource, path: String, key: String) {
        val download = when (source) {
            PhotoSource.POST -> posts.downloadPhoto(path)
            PhotoSource.GROUP -> groups.downloadPhoto(path)
        }
        when (val result = download) {
            is AppResult.Success -> {
                val image = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(result.value, 0, result.value.size)?.asImageBitmap()
                }
                if (image == null) {
                    Log.w(TAG, "A post photo is not a valid image")
                    states[key] = PhotoState.Failed
                } else if (states.containsKey(key)) {
                    remember(key, image)
                }
            }
            is AppResult.Failure -> {
                Log.w(TAG, "A post photo could not be downloaded: ${result.error}")
                if (states.containsKey(key)) states[key] = PhotoState.Failed
            }
        }
    }

    private fun remember(path: String, image: ImageBitmap) {
        states[path] = PhotoState.Loaded(image)
        loadedOrder.addLast(path)
        loadedBytes += image.byteSize()
        while (loadedBytes > MAX_BYTES && loadedOrder.size > 1) {
            val oldest = loadedOrder.removeFirst()
            (states.remove(oldest) as? PhotoState.Loaded)?.let { loadedBytes -= it.image.byteSize() }
        }
    }

    private fun ImageBitmap.byteSize(): Long = width.toLong() * height * 4

    private companion object {
        const val TAG = "PostPhotoLoader"
        const val MAX_BYTES = 48L * 1024 * 1024
    }
}
