package com.kampusagi.android.presentation.community

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.PostScope
import com.kampusagi.android.domain.usecase.CreatePostUseCase
import com.kampusagi.android.domain.usecase.PostTextValidator
import com.kampusagi.android.presentation.main.CreatePostRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val createPost: CreatePostUseCase,
) : ViewModel() {

    var scope by mutableStateOf(savedStateHandle.toRoute<CreatePostRoute>().scope)
        private set

    var body by mutableStateOf("")
        private set

    var isSubmitting by mutableStateOf(false)
        private set

    var error by mutableStateOf<AppError?>(null)
        private set

    /** The scope the post was shared in, once it is saved. */
    var createdIn by mutableStateOf<PostScope?>(null)
        private set

    val canSubmit: Boolean get() = !isSubmitting && PostTextValidator.isValidPost(body)

    fun onScopeChange(value: PostScope) {
        scope = value
    }

    fun onBodyChange(value: String) {
        if (value.length <= PostTextValidator.MAX_POST_LENGTH) body = value
        error = null
    }

    fun submit() {
        if (!canSubmit) return
        isSubmitting = true
        error = null
        viewModelScope.launch {
            when (val result = createPost(scope, body)) {
                is AppResult.Success -> createdIn = scope
                is AppResult.Failure -> error = result.error
            }
            isSubmitting = false
        }
    }
}
