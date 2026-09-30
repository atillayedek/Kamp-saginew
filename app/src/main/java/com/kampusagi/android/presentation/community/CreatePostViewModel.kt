package com.kampusagi.android.presentation.community

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.NewPost
import com.kampusagi.android.domain.model.PostCategory
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.repository.ImageEncoder
import com.kampusagi.android.domain.usecase.CreatePostUseCase
import com.kampusagi.android.domain.usecase.NewPostValidator
import com.kampusagi.android.domain.usecase.PostTextValidator
import com.kampusagi.android.domain.usecase.PriceFormat
import com.kampusagi.android.presentation.main.CreatePostRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A photo ready to upload, with a preview for the form. */
class PickedPhoto(val jpeg: ByteArray, val preview: ImageBitmap)

enum class PollDuration(val length: Duration?) {
    OPEN(null),
    ONE_DAY(Duration.ofDays(1)),
    THREE_DAYS(Duration.ofDays(3)),
    ONE_WEEK(Duration.ofDays(7)),
}

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val createPost: CreatePostUseCase,
    private val imageEncoder: ImageEncoder,
) : ViewModel() {

    var scope by mutableStateOf(savedStateHandle.toRoute<CreatePostRoute>().scope)
        private set

    var category by mutableStateOf(PostCategory.GENERAL)
        private set

    var body by mutableStateOf("")
        private set

    var photos by mutableStateOf<List<PickedPhoto>>(emptyList())
        private set

    var isPreparingPhotos by mutableStateOf(false)
        private set

    var pollEnabled by mutableStateOf(false)
        private set

    var pollOptions by mutableStateOf(listOf("", ""))
        private set

    var pollDuration by mutableStateOf(PollDuration.OPEN)
        private set

    var eventDate by mutableStateOf<LocalDate?>(null)
        private set

    var eventTime by mutableStateOf<LocalTime?>(null)
        private set

    var eventLocation by mutableStateOf("")
        private set

    var priceText by mutableStateOf("")
        private set

    var isSubmitting by mutableStateOf(false)
        private set

    var error by mutableStateOf<AppError?>(null)
        private set

    /** The scope the post was shared in, once it is saved. */
    var createdIn by mutableStateOf<PostScope?>(null)
        private set

    val remainingPhotos: Int get() = NewPostValidator.MAX_PHOTOS - photos.size

    /** Blank means no price; otherwise it must parse. */
    val priceValid: Boolean get() = priceText.isBlank() || PriceFormat.parseLira(priceText) != null

    val pollValid: Boolean get() = !pollEnabled || NewPostValidator.isValidPollOptions(pollOptions)

    /** A time without a date (or the reverse) is not a complete event date. */
    val eventValid: Boolean get() = (eventDate == null) == (eventTime == null)

    val canSubmit: Boolean
        get() = !isSubmitting && !isPreparingPhotos && PostTextValidator.isValidPost(body) &&
            pollValid && eventValid && (category != PostCategory.MARKETPLACE || priceValid)

    fun onScopeChange(value: PostScope) {
        scope = value
    }

    fun onCategoryChange(value: PostCategory) {
        category = value
        error = null
    }

    fun onBodyChange(value: String) {
        if (value.length <= PostTextValidator.MAX_POST_LENGTH) body = value
        error = null
    }

    fun onPhotosPicked(uris: List<String>) {
        val take = uris.take(remainingPhotos)
        if (take.isEmpty()) return
        isPreparingPhotos = true
        error = null
        viewModelScope.launch {
            for (uri in take) {
                when (val result = imageEncoder.postPhotoJpeg(uri)) {
                    is AppResult.Success -> {
                        val preview = withContext(Dispatchers.Default) {
                            BitmapFactory.decodeByteArray(result.value, 0, result.value.size)?.asImageBitmap()
                        }
                        if (preview == null) {
                            error = AppError.IMAGE_UNREADABLE
                        } else if (photos.size < NewPostValidator.MAX_PHOTOS) {
                            photos = photos + PickedPhoto(result.value, preview)
                        }
                    }
                    is AppResult.Failure -> error = result.error
                }
            }
            isPreparingPhotos = false
        }
    }

    fun removePhoto(index: Int) {
        photos = photos.filterIndexed { i, _ -> i != index }
    }

    fun onPollToggle(enabled: Boolean) {
        pollEnabled = enabled
        error = null
    }

    fun onPollOptionChange(index: Int, value: String) {
        if (value.length <= NewPostValidator.MAX_POLL_OPTION_LENGTH) {
            pollOptions = pollOptions.mapIndexed { i, old -> if (i == index) value else old }
        }
    }

    fun addPollOption() {
        if (pollOptions.size < NewPostValidator.MAX_POLL_OPTIONS) pollOptions = pollOptions + ""
    }

    fun removePollOption(index: Int) {
        if (pollOptions.size > NewPostValidator.MIN_POLL_OPTIONS) pollOptions = pollOptions.filterIndexed { i, _ -> i != index }
    }

    fun onPollDurationChange(value: PollDuration) {
        pollDuration = value
    }

    fun onEventDateChange(value: LocalDate?) {
        eventDate = value
        if (value == null) eventTime = null
    }

    fun onEventTimeChange(value: LocalTime?) {
        eventTime = value
    }

    fun onEventLocationChange(value: String) {
        if (value.length <= NewPostValidator.MAX_LOCATION_LENGTH) eventLocation = value
    }

    fun onPriceChange(value: String) {
        if (value.length <= 16) priceText = value
        error = null
    }

    fun submit() {
        if (!canSubmit) return
        isSubmitting = true
        error = null
        val zone = ZoneId.systemDefault()
        val isEvent = category == PostCategory.EVENT
        val date = eventDate
        val time = eventTime
        val startsAt = if (isEvent && date != null && time != null) date.atTime(time).atZone(zone).toOffsetDateTime().toString() else null
        val post = NewPost(
            scope = scope,
            category = category,
            body = body,
            photos = photos.map { it.jpeg },
            pollOptions = if (pollEnabled) pollOptions else null,
            pollClosesAt = if (pollEnabled) pollDuration.length?.let { OffsetDateTime.now(zone).plus(it).toString() } else null,
            eventStartsAt = startsAt,
            // The server stores a place only together with a date.
            eventLocation = if (startsAt != null) eventLocation.ifBlank { null } else null,
            priceKurus = if (category == PostCategory.MARKETPLACE) PriceFormat.parseLira(priceText) else null,
        )
        viewModelScope.launch {
            when (val result = createPost(post)) {
                is AppResult.Success -> createdIn = scope
                is AppResult.Failure -> error = result.error
            }
            isSubmitting = false
        }
    }
}
