package com.kampusagi.android.presentation.common.avatar

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kampusagi.android.core.di.ApplicationScope
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.repository.ProfileMediaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads profile photos for whoever is on screen. Requests are batched into one
 * `avatar_paths` call, then each photo is downloaded once with the session and
 * kept in memory. People without a photo keep their initials avatar.
 */
@Singleton
class AvatarLoader @Inject constructor(
    private val repository: ProfileMediaRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val images = mutableStateMapOf<String, ImageBitmap>()
    private val requested = HashSet<String>()
    private val pending = Channel<String>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.Main) {
            while (true) {
                val batch = mutableSetOf(pending.receive())
                delay(BATCH_WINDOW_MS)
                while (true) batch += pending.tryReceive().getOrNull() ?: break
                load(batch)
            }
        }
    }

    /** The photo if it is loaded; null shows the initials. Must be called on the main thread. */
    fun image(userId: String): ImageBitmap? = images[userId]

    fun request(userId: String) {
        if (requested.add(userId)) pending.trySend(userId)
    }

    /** After the signed-in person changes their photo. */
    fun invalidate(userId: String) {
        images.remove(userId)
        requested.remove(userId)
        request(userId)
    }

    /** On sign-out, so the next person starts clean. */
    fun clear() {
        images.clear()
        requested.clear()
    }

    private suspend fun load(userIds: Set<String>) {
        val paths = when (val result = repository.avatarPaths(userIds)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> {
                Log.w(TAG, "Avatar paths could not be loaded: ${result.error}")
                requested.removeAll(userIds) // a later screen asks again
                return
            }
        }
        for ((userId, path) in paths) {
            when (val bytes = repository.downloadAvatar(path)) {
                is AppResult.Success -> {
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes.value, 0, bytes.value.size)?.asImageBitmap()
                    }
                    if (bitmap != null) images[userId] = bitmap else Log.w(TAG, "Avatar $path is not a valid image")
                }
                is AppResult.Failure -> {
                    Log.w(TAG, "Avatar $path could not be downloaded: ${bytes.error}")
                    requested.remove(userId)
                }
            }
        }
    }

    private companion object {
        const val TAG = "AvatarLoader"
        const val BATCH_WINDOW_MS = 60L
    }
}
