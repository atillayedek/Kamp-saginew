package com.kampusagi.android.presentation.common.media

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kampusagi.android.core.di.ApplicationScope
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.CommunityRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PhotoState {
    data object Loading : PhotoState
    data class Loaded(val image: ImageBitmap) : PhotoState
    data object Failed : PhotoState
}

/**
 * Downloads post photos with the signed-in session (the bucket is private) and
 * keeps the most recently shown ones in memory, up to [MAX_BYTES].
 */
@Singleton
class PostPhotoLoader @Inject constructor(
    private val repository: CommunityRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val states = mutableStateMapOf<String, PhotoState>()
    private val loadedOrder = ArrayDeque<String>()
    private var loadedBytes = 0L

    /** Loading until [request] finishes. Must be called on the main thread. */
    fun state(path: String): PhotoState = states[path] ?: PhotoState.Loading

    fun request(path: String) {
        if (states[path] != null) return
        states[path] = PhotoState.Loading
        scope.launch(Dispatchers.Main) { load(path) }
    }

    fun retry(path: String) {
        if (states[path] == PhotoState.Failed) {
            states.remove(path)
            request(path)
        }
    }

    /** On sign-out, so the next person starts clean. */
    fun clear() {
        states.clear()
        loadedOrder.clear()
        loadedBytes = 0
    }

    private suspend fun load(path: String) {
        when (val result = repository.downloadPhoto(path)) {
            is AppResult.Success -> {
                val image = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(result.value, 0, result.value.size)?.asImageBitmap()
                }
                if (image == null) {
                    Log.w(TAG, "Photo $path is not a valid image")
                    states[path] = PhotoState.Failed
                } else if (states.containsKey(path)) {
                    remember(path, image)
                }
            }
            is AppResult.Failure -> {
                Log.w(TAG, "Photo $path could not be downloaded: ${result.error}")
                if (states.containsKey(path)) states[path] = PhotoState.Failed
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
